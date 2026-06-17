-- set_logs: workout history, one row per logged exercise entry.
--
-- Per-set reps are stored as an integer array (`reps`), weight as `numeric`
-- (kg) and rir as a `smallint`, matching the Recomp logging model. RLS
-- restricts rows to their owner via `user_id = auth.uid()`.

create table public.set_logs (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users (id) on delete cascade,
  plan_exercise_id uuid not null references public.plan_exercises (id) on delete cascade,
  date date,
  weight numeric,
  reps integer[],
  rir smallint,
  logged_at timestamptz not null default now(),
  source edit_source not null default 'app',
  updated_at timestamptz not null default now()
);

alter table public.set_logs enable row level security;

create policy "set_logs_owner" on public.set_logs
  for all
  to authenticated
  using (user_id = auth.uid())
  with check (user_id = auth.uid());

create trigger set_logs_set_updated_at
  before update on public.set_logs
  for each row
  execute function public.set_updated_at();

grant select, insert, update, delete on public.set_logs to authenticated;
