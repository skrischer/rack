-- Phase 17 (custom / user-authored exercises): let the catalog hold user-owned
-- custom exercises, owner-scoped by RLS, alongside the shared seed catalog.
--
-- This migration is ADDITIVE on top of Phase 14 (#188): it drops no column and
-- reuses the existing `exercises` schema. A custom exercise is simply an
-- `exercises` row whose `user_id` is set; a catalog row keeps `user_id IS NULL`.
-- The MCP `create_exercise` tool (issue #203 — NOT in this migration) injects
-- `user_id = auth.uid()` from the auth context, never from caller input.
-- See docs/specs/spec-custom-exercises.md.
--
-- New, additive:
--   user_id  owner of a custom exercise; NULL marks a shared catalog row. FK to
--            auth.users with ON DELETE CASCADE so a user's customs vanish with the
--            account, exactly like every other user-owned table.
--
-- RLS becomes a mixed model and therefore uses DISTINCT per-action policies, NOT
-- one `FOR ALL`: a `FOR ALL` policy would narrow SELECT to own rows and hide the
-- shared catalog from authenticated users. Instead:
--   SELECT  catalog (user_id IS NULL) OR own custom (user_id = auth.uid()); anon's
--           auth.uid() is null, so anon sees catalog only — never any custom.
--   INSERT  own customs only; a catalog insert (user_id NULL) fails WITH CHECK,
--           so no API role can write catalog rows (the seed loads as table owner,
--           bypassing RLS).
--   UPDATE  own customs only, on both visibility (USING) and the new row (CHECK).
--   DELETE  own customs only. A custom referenced by a plan is FK-RESTRICT
--           protected by plan_exercises.exercise_id (no change here).

alter table public.exercises
  add column if not exists user_id uuid references auth.users (id) on delete cascade;

-- Own-custom lookup: partial index covers only the (sparse) custom rows; catalog
-- rows (user_id IS NULL) stay out of the index.
create index if not exists exercises_user_id_idx
  on public.exercises (user_id)
  where user_id is not null;

alter table public.exercises enable row level security;

-- Replace the Phase 1/14 pure-public SELECT policy with the compound catalog-or-own
-- predicate. Roles stay anon + authenticated: anon keeps catalog-only read because
-- its auth.uid() is null (the `user_id = auth.uid()` disjunct never matches).
drop policy if exists "exercises_public_read" on public.exercises;
create policy "exercises_public_read" on public.exercises
  for select
  to anon, authenticated
  using (user_id is null or user_id = auth.uid());

-- Owner-scoped write policies (authenticated only); each enforces user_id = auth.uid()
-- so a user can only ever write its own customs, never a catalog row.
drop policy if exists "exercises_insert_own" on public.exercises;
create policy "exercises_insert_own" on public.exercises
  for insert
  to authenticated
  with check (user_id = auth.uid());

drop policy if exists "exercises_update_own" on public.exercises;
create policy "exercises_update_own" on public.exercises
  for update
  to authenticated
  using (user_id = auth.uid())
  with check (user_id = auth.uid());

drop policy if exists "exercises_delete_own" on public.exercises;
create policy "exercises_delete_own" on public.exercises
  for delete
  to authenticated
  using (user_id = auth.uid());

-- anon keeps catalog read only; authenticated gains write grants (RLS above scopes
-- those writes to own customs).
grant select on public.exercises to anon, authenticated;
grant insert, update, delete on public.exercises to authenticated;
