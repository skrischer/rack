/**
 * Supabase client factories for the rack-MCP.
 *
 * Two distinct clients exist by design (see docs/constitution.md):
 * - the admin client uses the service-role key and is used ONLY for the
 *   `api_keys` auth-resolution lookup, never for user data;
 * - the user-scoped client carries a freshly minted user JWT so every read and
 *   write runs under Postgres RLS as the resolved user.
 *
 * Both factories install a per-request timeout on `fetch` ({@link timeoutFetch}):
 * the MCP runs as a remote container talking to a managed Supabase over the
 * internet, where an idle keep-alive socket can be silently dropped by a network
 * intermediary. Reusing such a dead socket for the next request — e.g. a
 * `get_plan` read right after a write burst — otherwise stalls with no
 * client-side bound (beta §4.5: ~4-minute hang). The cap turns that indefinite
 * stall into a prompt, loud, retryable error while keeping the per-request,
 * user-scoped, stateless model unchanged (no sessions, no shared mutable state).
 */

import { createClient, type SupabaseClient } from '@supabase/supabase-js';

import type { Config } from './config.js';

/** Supabase client options that keep the MCP stateless (no token persistence). */
const STATELESS_AUTH = {
  autoRefreshToken: false,
  persistSession: false,
} as const;

/**
 * Hard cap on any single Supabase HTTP request. Generous enough never to trip a
 * legitimately slow query, far below the multi-minute stall a dead keep-alive
 * socket would otherwise produce (beta §4.5).
 */
const REQUEST_TIMEOUT_MS = 30_000;

/**
 * Wraps the global `fetch` so every call carries a fresh per-request timeout
 * signal (combined with any caller-supplied signal). On timeout the request is
 * aborted, which also discards the underlying (possibly half-open) socket, so a
 * retry gets a fresh connection. Stateless: the closure captures only the
 * timeout constant, so one instance is safe to share across requests and clients.
 */
export function timeoutFetch(timeoutMs: number): typeof fetch {
  return (input, init) => {
    const timeout = AbortSignal.timeout(timeoutMs);
    const signal = init?.signal != null ? AbortSignal.any([init.signal, timeout]) : timeout;
    return fetch(input, { ...init, signal });
  };
}

/**
 * Builds the service-role admin client. Restricted to the auth-resolution
 * lookup against `api_keys`; it MUST NOT touch user-owned data tables.
 */
export function createAdminClient(config: Config): SupabaseClient {
  return createClient(config.supabaseUrl, config.supabaseServiceRoleKey, {
    auth: STATELESS_AUTH,
    global: { fetch: timeoutFetch(REQUEST_TIMEOUT_MS) },
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
    global: {
      headers: { Authorization: `Bearer ${accessToken}` },
      fetch: timeoutFetch(REQUEST_TIMEOUT_MS),
    },
  });
}
