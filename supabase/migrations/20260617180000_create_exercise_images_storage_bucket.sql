-- Public-read Storage bucket for wger CC-BY-SA exercise images.
--
-- Phase 7's exercise detail screen loads exercise images by resolving the
-- catalog's `image_path` (added in 20260617170000) to a public Storage URL the
-- Android anon client fetches without a JWT or signed URL. This migration
-- creates the dedicated PUBLIC bucket those images live in and the read policy
-- the anon role uses.
--
-- The bucket holds ONLY attributed CC-BY-SA exercise images (no user data): it
-- mirrors the public-read catalog, not the per-user `artifacts` bucket. Reads
-- are open to anon + authenticated; writes are NOT granted to any Data API role
-- — only the seed/enrichment routine (running as the table/bucket owner, which
-- bypasses RLS) populates it, so the public surface is read-only. See
-- docs/specs/spec-exercise-detail.md.

-- Create the public bucket idempotently so a re-applied migration / db reset
-- never fails on a duplicate id. `image_path` is a relative path into this
-- bucket.
insert into storage.buckets (id, name, public)
values ('exercise-images', 'exercise-images', true)
on conflict (id) do nothing;

-- Public read on `storage.objects`, scoped to the `exercise-images` bucket.
-- `storage.objects` already has RLS enabled in the local stack, so only the
-- policy is added. No insert/update/delete policy is created for anon or
-- authenticated, so the bucket is read-only over the Data API; writes flow only
-- through the owner-privileged enrichment routine.
create policy "exercise_images_public_read" on storage.objects
  for select
  to anon, authenticated
  using (bucket_id = 'exercise-images');
