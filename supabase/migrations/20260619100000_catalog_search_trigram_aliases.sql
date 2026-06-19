-- Phase 14 (catalog search): make the exercise catalog agent-resolvable with
-- relevance-ranked, alias-aware, typo-tolerant trigram search.
--
-- This migration is ADDITIVE and reuses the existing Phase 1 / Phase 7 columns
-- (`name`, `muscles`/`primary_muscles`/`secondary_muscles`, `equipment`,
-- `category`). It drops NO column. It adds the two new search columns, enables
-- the trigram + unaccent extensions, and creates the GIN trigram index that
-- backs the rebuilt `search_exercises` tool (issue #190 — NOT touched here).
-- See docs/specs/spec-catalog-search.md.
--
-- New, additive:
--   aliases       alternate names matched alongside `name` (e.g. wger non-English
--                 translations + curated variants like "Seitheben" → Lateral
--                 Raise). Seeding is issue #189; this migration only adds the
--                 column (text[], nullable — an unseeded row has no aliases).
--   is_canonical  marks the curated base entry for a movement; the search
--                 ranking tiebreak (canonical-first). NOT NULL DEFAULT false so
--                 every existing/new row is non-canonical until curation flags
--                 it (issue #189).
--
-- The catalog is global seed data, not user-owned, so it carries no `auth.uid()`
-- owner policy and gains NO `user_id` in this phase. The Phase 1 public SELECT
-- policy (`exercises_public_read`, anon + authenticated) is re-asserted here so
-- the same-migration RLS gate is satisfied and anon keeps catalog read access
-- (precedent: the enrich migration). RLS stays enabled.

-- Trigram similarity carries both matching and ranking for the short catalog
-- names (no tsvector/FTS). `unaccent` is enabled so the search layer (#190) can
-- fold umlauts (ä/ö/ü) — a German alias query matches its de-accented variant.
create extension if not exists pg_trgm;
create extension if not exists unaccent;

alter table public.exercises
  add column if not exists aliases text[],
  add column if not exists is_canonical boolean not null default false;

-- Searchable text = name plus every alias, as one string. The rebuilt search
-- matches each `q` token against THIS expression via pg_trgm word-similarity
-- (the `<%` operator / `word_similarity()`, which finds the token inside the
-- longer text) and ranks by that score, so a single GIN trigram index covers
-- both `name` and the `aliases` array under one ranking system (no second
-- index/ranking).
--
-- A wrapper is required: a GIN trigram expression index demands an IMMUTABLE
-- expression, but `array_to_string` is only STABLE and `aliases::text` is not
-- immutable either, so neither can be inlined into the index. This SQL function
-- is pure, deterministic string concatenation over its arguments, so declaring
-- it IMMUTABLE is correct; it references only the pg_catalog `array_to_string`,
-- giving no search_path hijack surface.
create or replace function public.exercises_search_text(name text, aliases text[])
  returns text
  language sql
  immutable
  as $$
    select name || ' ' || coalesce(array_to_string(aliases, ' '), '')
  $$;

-- GIN trigram index over name + aliases, backing the `<%` / `%` operators and
-- similarity ranking in `search_exercises`. The matching query must reference
-- the same `exercises_search_text(name, aliases)` expression to use this index.
create index if not exists exercises_search_trgm
  on public.exercises
  using gin (public.exercises_search_text(name, aliases) gin_trgm_ops);

-- Re-assert public read access (idempotent: drop-if-exists then recreate).
alter table public.exercises enable row level security;

drop policy if exists "exercises_public_read" on public.exercises;
create policy "exercises_public_read" on public.exercises
  for select
  to anon, authenticated
  using (true);

grant select on public.exercises to anon, authenticated;
