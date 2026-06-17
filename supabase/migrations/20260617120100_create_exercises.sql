-- exercises: the global exercise catalog seeded from the wger CC-BY-SA dataset.
--
-- This is the only non-user-owned table: it is a shared catalog with public
-- read access (anon + authenticated SELECT) and no owner policy. The seed
-- (issue #5) populates `license`/`license_author` per row for CC-BY-SA
-- attribution. It carries no `source`/`updated_at` because it is reference data,
-- not user-mutated training data.

create table public.exercises (
  id uuid primary key default gen_random_uuid(),
  name text not null,
  category text,
  muscles text[],
  equipment text[],
  license text,
  license_author text
);

alter table public.exercises enable row level security;

-- Public catalog: readable by anon and authenticated, writable by no API role
-- (the seed loads as the table owner, which bypasses RLS).
create policy "exercises_public_read" on public.exercises
  for select
  to anon, authenticated
  using (true);

grant select on public.exercises to anon, authenticated;
