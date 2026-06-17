-- plan_exercises: exercises placed on a plan day, referencing the catalog.
--
-- Carries a denormalized `user_id` for the uniform `user_id = auth.uid()` owner
-- policy alongside the structural `day_id`/`exercise_id` foreign keys. `target`
-- holds the sets x reps prescription and `superset_label` groups supersets; both
-- and the rir/cue fields are free text/values validated at the MCP boundary in
-- Phase 2. `exercise_id` references the shared catalog and is restricted from
-- deletion (no cascade) so catalog rows are never removed out from under a plan.

create table public.plan_exercises (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users (id) on delete cascade,
  day_id uuid not null references public.plan_days (id) on delete cascade,
  exercise_id uuid not null references public.exercises (id) on delete restrict,
  position integer not null,
  target text,
  rir smallint,
  cue text,
  superset_label text,
  source edit_source not null default 'app',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

alter table public.plan_exercises enable row level security;

create policy "plan_exercises_owner" on public.plan_exercises
  for all
  to authenticated
  using (user_id = auth.uid())
  with check (user_id = auth.uid());

create trigger plan_exercises_set_updated_at
  before update on public.plan_exercises
  for each row
  execute function public.set_updated_at();

grant select, insert, update, delete on public.plan_exercises to authenticated;
