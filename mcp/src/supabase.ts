/**
 * Supabase client factories for the rack-MCP.
 *
 * Two distinct clients exist by design (see docs/constitution.md):
 * - the admin client uses the service-role key and is used ONLY for the
 *   `api_keys` auth-resolution lookup, never for user data;
 * - the user-scoped client carries a freshly minted user JWT so every read and
 *   write runs under Postgres RLS as the resolved user.
 */

import { createClient, type SupabaseClient } from '@supabase/supabase-js';

import type { Config } from './config.js';

/** Supabase client options that keep the MCP stateless (no token persistence). */
const STATELESS_AUTH = {
  autoRefreshToken: false,
  persistSession: false,
} as const;

/**
 * Builds the service-role admin client. Restricted to the auth-resolution
 * lookup against `api_keys`; it MUST NOT touch user-owned data tables.
 */
export function createAdminClient(config: Config): SupabaseClient {
  return createClient(config.supabaseUrl, config.supabaseServiceRoleKey, {
    auth: STATELESS_AUTH,
  });
}

/**
 * Builds a per-request client that authenticates as the resolved user via the
 * given minted access token, so RLS (`user_id = auth.uid()`) applies to every
 * query made through it.
 */
export function createUserClient(config: Config, accessToken: string): SupabaseClient {
  return createClient(config.supabaseUrl, config.supabaseAnonKey, {
    auth: STATELESS_AUTH,
    global: { headers: { Authorization: `Bearer ${accessToken}` } },
  });
}
