-- Grant the service-role DML on api_keys for the rack-MCP auth lookup.
--
-- The rack-MCP resolves an incoming API key by hashing it and looking the row
-- up in `api_keys`, then stamps `last_used_at` and (in a later phase) inserts a
-- newly minted key. This is the ONE place the MCP uses the service-role client
-- (see docs/specs/spec-rack-mcp.md); user-owned data is always accessed through
-- a user-scoped client under RLS. Phase 1 granted these privileges only to
-- `authenticated`, so the service-role lookup is denied without this grant.
--
-- No RLS policy is added: `service_role` carries BYPASSRLS by design and is the
-- trusted server-side auth-resolution role. The existing `api_keys_owner` policy
-- continues to scope all `authenticated` (app/user JWT) access to the owner.

grant select, insert, update, delete on public.api_keys to service_role;
