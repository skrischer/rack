-- Enrich the global exercises catalog with execution-detail fields.
--
-- Phase 7 adds an in-app exercise detail screen (how-to instructions, target
-- muscles, equipment, image). This migration adds the catalog-side fields it
-- reads while REUSING the existing Phase 1 columns (`muscles`, `equipment`,
-- `category`, `license`, `license_author`) — it adds NO second `muscles` or
-- `equipment` column and does not drop `muscles` (a destructive change to Phase 1
-- data). The existing `muscles` column is retained as the legacy union; the
-- enrichment routine backfills `primary_muscles`/`secondary_muscles` where the
-- wger/free-exercise-db sources distinguish them. See
-- docs/specs/spec-exercise-detail.md.
--
-- New, all nullable and additive:
--   instructions       ordered step-by-step how-to text (text[])
--   primary_muscles    primary target muscles distinguished by source (text[])
--   secondary_muscles  secondary target muscles distinguished by source (text[])
--   image_path         relative path into the public exercise-images bucket (text)
--
-- The catalog is global seed data, not user-owned, so it carries no
-- `auth.uid()` owner policy. The Phase 1 public SELECT policy
-- (`exercises_public_read`, anon + authenticated) is re-asserted here so the
-- same-migration RLS gate is satisfied and the anon role keeps catalog read
-- access. RLS stays enabled.

alter table public.exercises
  add column if not exists instructions text[],
  add column if not exists primary_muscles text[],
  add column if not exists secondary_muscles text[],
  add column if not exists image_path text;

-- Re-assert public read access (idempotent: drop-if-exists then recreate).
alter table public.exercises enable row level security;

drop policy if exists "exercises_public_read" on public.exercises;
create policy "exercises_public_read" on public.exercises
  for select
  to anon, authenticated
  using (true);

grant select on public.exercises to anon, authenticated;
