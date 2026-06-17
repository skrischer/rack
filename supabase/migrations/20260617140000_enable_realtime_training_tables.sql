-- Enable Supabase Realtime for the user-owned training tables.
--
-- The Android app subscribes to per-user row changes so agent writes
-- (`source='agent'`) land live and highlighted (see docs/specs/spec-live-sync.md).
-- Realtime delivers row events only for tables in the `supabase_realtime`
-- publication, and RLS-authorized Realtime needs full row data to evaluate the
-- owner policy on UPDATE/DELETE WAL rows, so each table is set to
-- REPLICA IDENTITY FULL.
--
-- The four tables and their Phase 1 owner RLS policies (`user_id = auth.uid()`)
-- already exist; this migration adds no table and no policy and references no
-- service-role bypass. Isolation stays enforced by those existing policies via
-- RLS-authorized Realtime.

alter table public.plans replica identity full;
alter table public.plan_days replica identity full;
alter table public.plan_exercises replica identity full;
alter table public.set_logs replica identity full;

alter publication supabase_realtime add table public.plans;
alter publication supabase_realtime add table public.plan_days;
alter publication supabase_realtime add table public.plan_exercises;
alter publication supabase_realtime add table public.set_logs;
