-- api_keys: per-user opaque MCP API keys, stored only as a hash.
--
-- The plaintext key is shown to the user once at creation; only `key_hash` is
-- persisted. RLS restricts rows to their owner via `user_id = auth.uid()`.

create table public.api_keys (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users (id) on delete cascade,
  key_hash text not null,
  name text,
  last_used_at timestamptz,
  revoked boolean not null default false,
  source edit_source not null default 'app',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

alter table public.api_keys enable row level security;

create policy "api_keys_owner" on public.api_keys
  for all
  to authenticated
  using (user_id = auth.uid())
  with check (user_id = auth.uid());

create trigger api_keys_set_updated_at
  before update on public.api_keys
  for each row
  execute function public.set_updated_at();

grant select, insert, update, delete on public.api_keys to authenticated;
