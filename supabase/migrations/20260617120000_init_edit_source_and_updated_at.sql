-- Shared database primitives for every mutation-carrying table.
--
-- `edit_source` distinguishes writes made by the app from writes made by the
-- user's agent (via the rack-MCP), so the app can highlight agent edits.
-- `set_updated_at()` is a single shared BEFORE UPDATE trigger function attached
-- to each mutation-carrying table, so both adapters inherit `updated_at`
-- maintenance from the database rather than from client code.

create type edit_source as enum ('app', 'agent');

create or replace function public.set_updated_at()
returns trigger
language plpgsql
as $$
begin
  new.updated_at = now();
  return new;
end;
$$;
