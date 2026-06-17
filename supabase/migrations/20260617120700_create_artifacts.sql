-- artifacts: agent-generated visualization artifacts, referenced in Storage.
--
-- `storage_path` points at the object in Supabase Storage (the bucket itself is
-- created in Phase 6). RLS restricts rows to their owner via
-- `user_id = auth.uid()`.

create table public.artifacts (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users (id) on delete cascade,
  name text,
  type text,
  storage_path text,
  source edit_source not null default 'app',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

alter table public.artifacts enable row level security;

create policy "artifacts_owner" on public.artifacts
  for all
  to authenticated
  using (user_id = auth.uid())
  with check (user_id = auth.uid());

create trigger artifacts_set_updated_at
  before update on public.artifacts
  for each row
  execute function public.set_updated_at();

grant select, insert, update, delete on public.artifacts to authenticated;
