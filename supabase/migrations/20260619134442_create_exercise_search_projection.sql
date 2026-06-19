-- Phase 17 (custom / user-authored exercises, issue #203): surface custom-ness in
-- the catalog search result. A custom exercise is an `exercises` row whose
-- `user_id` is set (#202); the user-scoped `search_exercises` RPC already returns
-- the caller's own customs alongside the catalog via RLS, but its projection did
-- not expose `user_id`, so a client could not tell a custom row from a catalog
-- row. This migration adds `user_id` to the function's RETURNS TABLE and SELECT so
-- the MCP tool's `EXERCISE_COLUMNS` projection can mark customs (a row is custom
-- when `user_id IS NOT NULL`). No separate `is_custom` column — custom-ness is
-- derived from `user_id` (spec decision). See docs/specs/spec-custom-exercises.md.
--
-- Adding an output column changes the function's return type, which CREATE OR
-- REPLACE cannot do (error 42P13), so the function is dropped and recreated; the
-- argument signature, body, ranking, and SECURITY INVOKER stance are unchanged
-- apart from the new `user_id` column. Grants are reasserted because DROP removes
-- them with the function.

drop function if exists public.search_exercises(text, text, text, text, integer);

create function public.search_exercises(
  search_query text default null,
  filter_equipment text default null,
  filter_muscle text default null,
  filter_category text default null,
  result_limit integer default 25
)
returns table (
  id uuid,
  name text,
  category text,
  muscles text[],
  equipment text[],
  license text,
  license_author text,
  aliases text[],
  is_canonical boolean,
  user_id uuid,
  score real,
  tiebreak real
)
language sql
stable
security invoker
set search_path = pg_catalog, public, extensions
as $$
  with tokens as (
    select tok
    from regexp_split_to_table(coalesce(btrim(search_query), ''), '\s+') as tok
    where tok <> ''
  )
  select
    e.id,
    e.name,
    e.category,
    e.muscles,
    e.equipment,
    e.license,
    e.license_author,
    e.aliases,
    e.is_canonical,
    -- Custom-ness indicator: NULL for a shared catalog row, the owner for a custom
    -- exercise. RLS already limits this to the caller's own customs plus catalog.
    e.user_id,
    coalesce(
      (select sum(word_similarity(t.tok, public.exercises_search_text(e.name, e.aliases)))
       from tokens t),
      0
    )::real as score,
    -- Secondary relevance tiebreak: whole-query trigram similarity to the NAME
    -- (not name+aliases — alias-rich rows must not be penalised). Ranked BELOW
    -- is_canonical, it favours concise, on-point name matches when the summed
    -- per-token score ties (e.g. the single token "press" scores 1.0 on dozens of
    -- rows; this floats "Leg Press" / "Bench Press" above six-word names and above
    -- alias-only matches instead of truncating alphabetically).
    similarity(coalesce(btrim(search_query), ''), e.name)::real as tiebreak
  from public.exercises e
  where
    (filter_category is null or e.category = filter_category)
    and (filter_equipment is null or filter_equipment = any(e.equipment))
    and (
      filter_muscle is null
      or filter_muscle = any(coalesce(nullif(e.primary_muscles, '{}'), e.muscles))
    )
    and (
      -- No query -> filter-only browse (every row that passed the filters).
      not exists (select 1 from tokens)
      -- token-AND: keep the row only if NO token falls below the match floor.
      or not exists (
        select 1
        from tokens t
        where word_similarity(t.tok, public.exercises_search_text(e.name, e.aliases)) < 0.4
      )
    )
  order by score desc, e.is_canonical desc, tiebreak desc, e.name asc
  -- Clamp 1..100 (default 25). The MCP tool's Zod schema also caps at 100, but
  -- the RPC is grantable to anon/authenticated and reachable directly via
  -- PostgREST, so the cap is re-enforced here so no caller can request an
  -- unbounded scan.
  limit greatest(least(coalesce(result_limit, 25), 100), 1)
$$;

-- Catalog search is public-read; expose the RPC to the same roles as the table.
revoke execute on function
  public.search_exercises(text, text, text, text, integer) from public;
grant execute on function
  public.search_exercises(text, text, text, text, integer) to anon, authenticated;
