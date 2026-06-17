-- plans: user-owned training plans.
--
-- `kind` is unconstrained free text in the schema (e.g. recomp/block); allowed
-- values are validated at the MCP boundary in Phase 2. RLS restricts every row
-- to its owner via `user_id = auth.uid()`.

create table public.plans (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users (id) on delete cascade,
  name text not null,
  kind text,
  source edit_source not null default 'app',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

alter table public.plans enable row level security;

create policy "plans_owner" on public.plans
  for all
  to authenticated
  using (user_id = auth.uid())
  with check (user_id = auth.uid());

create trigger plans_set_updated_at
  before update on public.plans
  for each row
  execute function public.set_updated_at();

grant select, insert, update, delete on public.plans to authenticated;
