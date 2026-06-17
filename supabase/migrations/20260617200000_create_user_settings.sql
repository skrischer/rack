-- user_settings: per-user app preferences, one row per user.
--
-- Keyed by `user_id` (PK) so the row is idempotent under the repository's
-- upsert-on-read provisioning. Weights are stored canonically in kilograms
-- elsewhere; `weight_unit` is a display/entry preference only. The four
-- `rest_*_seconds` columns hold the rest-timer defaults the timer feature reads.
-- RLS restricts every row to its owner via `user_id = auth.uid()`. Settings are
-- app-authored only this phase, but carry `source`/`updated_at` for uniformity.

create table public.user_settings (
  user_id uuid primary key references auth.users (id) on delete cascade,
  weight_unit text not null default 'kg',
  theme text not null default 'dark',
  rest_compound_seconds integer not null default 150,
  rest_isolation_seconds integer not null default 90,
  rest_superset_seconds integer not null default 90,
  rest_circuit_seconds integer not null default 45,
  display_name text not null default '',
  source edit_source not null default 'app',
  updated_at timestamptz not null default now()
);

alter table public.user_settings enable row level security;

create policy "user_settings_owner" on public.user_settings
  for all
  to authenticated
  using (user_id = auth.uid())
  with check (user_id = auth.uid());

create trigger user_settings_set_updated_at
  before update on public.user_settings
  for each row
  execute function public.set_updated_at();

grant select, insert, update, delete on public.user_settings to authenticated;
