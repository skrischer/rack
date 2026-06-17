-- api_keys: add a non-secret display fragment and a soft-delete timestamp.
--
-- The plaintext key is irretrievable after creation (only `key_hash` is stored),
-- so the in-app list cannot derive a recognizable fragment at read time —
-- `key_prefix` stores that short, non-secret fragment up front. `revoked_at`
-- records when a key was soft-deleted (the `revoked` boolean already carries the
-- state). Both columns are nullable additions; the existing `api_keys_owner` RLS
-- policy on `user_id = auth.uid()` is left unchanged and RLS stays enabled.
-- See docs/specs/spec-api-key-management.md.

alter table public.api_keys
  add column if not exists key_prefix text,
  add column if not exists revoked_at timestamptz;
