-- Private Storage bucket for agent-authored visualization artifacts.
--
-- The `artifacts` metadata table (Phase 1) already holds the row + RLS owner
-- policy; this migration adds the matching PRIVATE Storage bucket and per-user
-- access policies on `storage.objects`. Objects live under the path convention
-- `{user_id}/{artifact_id}.{ext}`, so a user (and an MCP acting as that user)
-- can only read/write objects whose first path segment equals their own
-- `auth.uid()`. No service-role assumption is baked in: isolation is enforced by
-- these path-prefix policies, mirroring the table RLS, so RLS applies to both an
-- app user and the MCP-as-user (see docs/specs/spec-visualization-artifacts.md).

-- Create the private bucket idempotently so a re-applied migration / db reset
-- never fails on a duplicate id.
insert into storage.buckets (id, name, public)
values ('artifacts', 'artifacts', false)
on conflict (id) do nothing;

-- Per-user access on `storage.objects`, scoped to the `artifacts` bucket and to
-- objects whose first path segment equals the caller's uid. `storage.objects`
-- already has RLS enabled and `authenticated` already holds the table grants in
-- the local stack, so only the policies are added here.

create policy "artifacts_owner_select" on storage.objects
  for select
  to authenticated
  using (
    bucket_id = 'artifacts'
    and (storage.foldername(name))[1] = (select auth.uid()::text)
  );

create policy "artifacts_owner_insert" on storage.objects
  for insert
  to authenticated
  with check (
    bucket_id = 'artifacts'
    and (storage.foldername(name))[1] = (select auth.uid()::text)
  );

create policy "artifacts_owner_update" on storage.objects
  for update
  to authenticated
  using (
    bucket_id = 'artifacts'
    and (storage.foldername(name))[1] = (select auth.uid()::text)
  )
  with check (
    bucket_id = 'artifacts'
    and (storage.foldername(name))[1] = (select auth.uid()::text)
  );

create policy "artifacts_owner_delete" on storage.objects
  for delete
  to authenticated
  using (
    bucket_id = 'artifacts'
    and (storage.foldername(name))[1] = (select auth.uid()::text)
  );
