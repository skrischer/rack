-- Phase 14 (catalog search, issue #190): the SQL backing for the rebuilt
-- `search_exercises` MCP tool — relevance-ranked, multi-word (token-AND),
-- alias-aware trigram search over the public catalog, with equipment / muscle /
-- category filters and a filter-only browse mode.
--
-- Why an RPC and not a PostgREST expression: the search is token-AND with a
-- SUMMED per-token word-similarity score — every whitespace token of `q` must
-- match name+aliases (via pg_trgm word-similarity), and rows rank by the sum of
-- the per-token scores. PostgREST cannot express "split q into tokens, require
-- all of them, sum their word_similarity" in a filter string, so the logic lives
-- in one SQL function the user-scoped client calls via `.rpc()`. RLS still
-- applies: the function is SECURITY INVOKER and runs as the calling role, which
-- reads `public.exercises` through its public-read policy (no service-role path).
--
-- Matching uses pg_trgm WORD-similarity (`word_similarity(token, text)`), NOT the
-- strict `%` similarity: each short token must be found WITHIN the longer
-- concatenated name+aliases text. The matching expression is exactly
-- `public.exercises_search_text(name, aliases)` (issue #188), so the GIN
-- `gin_trgm_ops` index over that helper backs it. unaccent folding is not applied
-- here: the shipped GIN index is over the raw helper, so wrapping the expression
-- in unaccent() would defeat the index, and no acceptance query needs it.
--
-- This migration adds NO table — only a function — so it ships no new RLS policy;
-- the existing `exercises_public_read` policy governs the reads inside it.
-- `search_path` includes `extensions` so `word_similarity` resolves wherever the
-- local stack installed pg_trgm; every catalog reference is schema-qualified.
-- See docs/specs/spec-catalog-search.md.

-- Per-token match floor (0.4): a token counts as matching a row only when its
-- word-similarity to the row's name+aliases clears it. Token-AND requires every
-- token to clear it; relevance ranking (summed similarity) then orders the
-- survivors. 0.4 keeps near-matches like "dumbbell" -> "Dumbbells" (~0.89) while
-- rejecting trigram-coincidental ones like "dumbbell" -> "Barbell" (~0.33).
create or replace function public.search_exercises(
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
  limit greatest(coalesce(result_limit, 25), 1)
$$;

-- Catalog search is public-read; expose the RPC to the same roles as the table.
revoke execute on function
  public.search_exercises(text, text, text, text, integer) from public;
grant execute on function
  public.search_exercises(text, text, text, text, integer) to anon, authenticated;
