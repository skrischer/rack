-- plan_days: ordered days within a plan.
--
-- Carries a denormalized `user_id` so the owner policy can resolve directly to
-- `user_id = auth.uid()` (the project's uniform RLS shape) in addition to the
-- structural `plan_id` foreign key. `tag` (push/pull/legs) is unconstrained free
-- text in the schema, validated at the MCP boundary in Phase 2.

create table public.plan_days (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users (id) on delete cascade,
  plan_id uuid not null references public.plans (id) on delete cascade,
  position integer not null,
  title text,
  focus text,
  tag text,
  source edit_source not null default 'app',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

alter table public.plan_days enable row level security;

create policy "plan_days_owner" on public.plan_days
  for all
  to authenticated
  using (user_id = auth.uid())
  with check (user_id = auth.uid());

create trigger plan_days_set_updated_at
  before update on public.plan_days
  for each row
  execute function public.set_updated_at();

grant select, insert, update, delete on public.plan_days to authenticated;
