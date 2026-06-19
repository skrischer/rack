-- Structured plan prescription & grouping on plan_exercises (Phase 15).
--
-- Replaces the free-text prescription with typed, evaluable fields and makes
-- exercise grouping explicit. The legacy free-text `target` (sets x reps), the
-- single `rir`, and the implicit `superset_label` are dropped; their roles are
-- taken by typed columns and an explicit per-day group key. The disposable beta
-- `plan_exercises` rows are cleared before the column swap (beta data is
-- throwaway; the catalog seed and every other table are untouched). A named
-- enum `plan_group_type` mirrors the `edit_source` enum precedent over an ad-hoc
-- CHECK on a text column. DB CHECK constraints enforce the range invariants in
-- the database so both the app and the MCP inherit them, and a partial update of
-- only one side of a pair cannot persist an inverted state. The owner RLS policy
-- is re-asserted in this migration per the constitution (every user-data table
-- ships its RLS with its migration). See
-- docs/specs/spec-structured-prescription.md.
--
-- New, all nullable:
--   sets          prescribed working-set count (smallint)
--   rep_min       lower bound of the rep range (smallint)
--   rep_max       upper bound of the rep range (smallint)
--   rir_low       lower bound of the RIR range (smallint)
--   rir_high      upper bound of the RIR range (smallint)
--   rest_seconds  rest after this exercise's set before the next (int); in a
--                 group the last member carries the group rest
--   superset_id   per-day group key binding members (smallint); day N and day M
--                 may each reuse group 1
--   group_type    distinguishes a superset from a circuit (plan_group_type)

create type plan_group_type as enum ('superset', 'circuit');

-- Beta plan_exercises rows are disposable; clear them before the column swap.
delete from public.plan_exercises;

alter table public.plan_exercises
  drop column target,
  drop column rir,
  drop column superset_label,
  add column sets smallint,
  add column rep_min smallint,
  add column rep_max smallint,
  add column rir_low smallint,
  add column rir_high smallint,
  add column rest_seconds integer,
  add column superset_id smallint,
  add column group_type plan_group_type;

alter table public.plan_exercises
  add constraint plan_exercises_rep_range_check check (rep_min <= rep_max),
  add constraint plan_exercises_rir_range_check check (rir_low <= rir_high);

-- Re-assert the owner RLS policy in the same migration (idempotent).
alter table public.plan_exercises enable row level security;

drop policy if exists "plan_exercises_owner" on public.plan_exercises;
create policy "plan_exercises_owner" on public.plan_exercises
  for all
  to authenticated
  using (user_id = auth.uid())
  with check (user_id = auth.uid());
