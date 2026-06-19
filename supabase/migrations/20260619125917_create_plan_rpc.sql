-- Transactional nested-create RPCs for agent plan authoring (Phase 16).
--
-- `create_plan` writes a whole plan tree (plan -> days -> each day's exercises)
-- and `create_plan_day` appends one day plus its exercises to an existing plan,
-- each in ONE transaction (a single function call is one statement) with every
-- row `source='agent'`. Any failure anywhere in the tree (FK on `exercise_id`, a
-- range CHECK, RLS) raises and rolls the WHOLE tree back, so nothing is partially
-- written; the error surfaces to the MCP as a loud string.
--
-- The functions are `SECURITY INVOKER` (the default, declared explicitly): they
-- run as the calling `authenticated` role and DO NOT escalate. They are never
-- `SECURITY DEFINER`/`SET ROLE`. RLS applies because PostgREST sets `auth.uid()`
-- from the user JWT the user-scoped client sends on the `.rpc()` call, so each
-- insert is owner-checked exactly like a per-row app/MCP write — there is no
-- service-role data path. Each insert injects `user_id = auth.uid()`; no
-- `user_id` is accepted from the caller.
--
-- The payload is camelCase at the MCP boundary (Zod-validated there, including
-- the rep/RIR range invariants) and mapped to snake_case columns INSIDE these
-- functions (quoted identifiers in `jsonb_to_recordset`). The exercise fields are
-- the Phase 15 typed prescription + per-day-local `superset_id`/`group_type`
-- grouping; the dropped legacy `target`/`rir`/`superset_label` are not used.
--
-- This migration adds NO table; it only adds functions, so it ships no new RLS
-- policy — the existing per-table owner policies enforce ownership inside the
-- transaction. `search_path` is pinned empty and every object is schema-qualified
-- to keep resolution deterministic. See docs/specs/spec-mcp-authoring.md.

-- Inserts one day and its exercises under an existing, caller-owned plan and
-- returns the new day id plus the inserted-exercise count. Shared by both public
-- RPCs. The RLS-scoped ownership guard means a missing or non-owned `p_plan_id`
-- fails loudly (and identically) before any write, so the helper is safe even
-- though `authenticated` can reach it directly: it can only ever append to a plan
-- the caller owns.
create or replace function public.insert_plan_day(p_plan_id uuid, p_day jsonb)
returns jsonb
language plpgsql
security invoker
set search_path = ''
as $$
declare
  v_day_id uuid;
  v_exercise_count integer;
begin
  if not exists (select 1 from public.plans where id = p_plan_id) then
    raise exception 'plan % not found', p_plan_id using errcode = 'no_data_found';
  end if;

  insert into public.plan_days (user_id, plan_id, position, title, focus, tag, source)
  values (auth.uid(), p_plan_id, (p_day->>'position')::integer,
          p_day->>'title', p_day->>'focus', p_day->>'tag', 'agent')
  returning id into v_day_id;

  insert into public.plan_exercises
    (user_id, day_id, exercise_id, position, sets, rep_min, rep_max,
     rir_low, rir_high, rest_seconds, cue, superset_id, group_type, source)
  select auth.uid(), v_day_id, e."exerciseId", e."position", e.sets, e."repMin", e."repMax",
         e."rirLow", e."rirHigh", e."restSeconds", e.cue, e."supersetId", e."groupType", 'agent'
  from jsonb_to_recordset(coalesce(p_day->'exercises', '[]'::jsonb)) as e(
    "exerciseId" uuid, "position" integer, sets smallint, "repMin" smallint, "repMax" smallint,
    "rirLow" smallint, "rirHigh" smallint, "restSeconds" integer, cue text,
    "supersetId" smallint, "groupType" public.plan_group_type);
  get diagnostics v_exercise_count = row_count;

  return jsonb_build_object('dayId', v_day_id, 'exerciseCount', v_exercise_count);
end;
$$;

-- Writes a whole plan tree (plan + days + each day's exercises) in one
-- transaction and returns { planId, dayCount, exerciseCount }.
create or replace function public.create_plan(payload jsonb)
returns jsonb
language plpgsql
security invoker
set search_path = ''
as $$
declare
  v_plan_id uuid;
  v_day jsonb;
  v_day_result jsonb;
  v_day_count integer := 0;
  v_exercise_count integer := 0;
begin
  insert into public.plans (user_id, name, kind, source)
  values (auth.uid(), payload->>'name', payload->>'kind', 'agent')
  returning id into v_plan_id;

  for v_day in select value from jsonb_array_elements(coalesce(payload->'days', '[]'::jsonb)) loop
    v_day_result := public.insert_plan_day(v_plan_id, v_day);
    v_day_count := v_day_count + 1;
    v_exercise_count := v_exercise_count + (v_day_result->>'exerciseCount')::integer;
  end loop;

  return jsonb_build_object(
    'planId', v_plan_id, 'dayCount', v_day_count, 'exerciseCount', v_exercise_count);
end;
$$;

-- Appends one day plus its exercises to an existing plan (payload carries the
-- planId and the day fields) and returns { planId, dayId, exerciseCount }.
create or replace function public.create_plan_day(payload jsonb)
returns jsonb
language plpgsql
security invoker
set search_path = ''
as $$
declare
  v_result jsonb;
begin
  -- The whole payload doubles as the day object; its extra `planId` key is
  -- harmlessly ignored (insert_plan_day reads only the day fields + exercises).
  v_result := public.insert_plan_day((payload->>'planId')::uuid, payload);
  return jsonb_build_object('planId', payload->>'planId') || v_result;
end;
$$;

-- Restrict to authenticated callers (anon has no owner RLS policy and could not
-- write anyway, but is denied execution explicitly). `insert_plan_day` is granted
-- to `authenticated` because the SECURITY INVOKER public RPCs call it as that role;
-- its direct-call surface is intentional and safe (the RLS-scoped ownership guard
-- and the per-row WITH CHECK confine it to plans the caller owns).
revoke execute on function public.insert_plan_day(uuid, jsonb) from public;
revoke execute on function public.create_plan(jsonb) from public;
revoke execute on function public.create_plan_day(jsonb) from public;
grant execute on function public.insert_plan_day(uuid, jsonb) to authenticated;
grant execute on function public.create_plan(jsonb) to authenticated;
grant execute on function public.create_plan_day(jsonb) to authenticated;
