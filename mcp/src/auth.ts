/**
 * Per-request API-key auth resolution and act-as-user context for the rack-MCP.
 *
 * Resolves an `Authorization: Bearer <key>` value to a Supabase user: the key is
 * peppered-SHA-256 hashed and matched (constant-time) against `api_keys` via the
 * service-role client (the ONLY user-facing use of service-role), then a
 * short-lived user-scoped token is minted and a per-request supabase-js client
 * built from it so RLS applies to all tool data access. Unknown, malformed,
 * revoked, or missing keys yield `null` so the caller can reject with 401 before
 * any tool runs. See docs/specs/spec-rack-mcp.md.
 */

import type { SupabaseClient } from '@supabase/supabase-js';

import { hashApiKey, hashesEqual, isWellFormedApiKey } from './apiKey.js';
import type { Config } from './config.js';
import { createUserClient } from './supabase.js';
import { mintUserToken } from './userToken.js';

/** Resolved per-request identity and the user-scoped client tools must use. */
export interface AuthContext {
  /** The Supabase user id the API key resolved to. */
  userId: string;
  /** A supabase-js client acting as `userId`, so RLS applies to every query. */
  supabase: SupabaseClient;
}

/** Minimal shape of the `api_keys` row needed to verify and resolve a key. */
interface ApiKeyRow {
  id: string;
  user_id: string;
  key_hash: string;
}

/**
 * Extracts the bearer key from an `Authorization` header value, or `null` when
 * the header is absent or not a well-formed bearer token.
 */
export function extractBearerKey(authorization: string | undefined): string | null {
  if (authorization === undefined) {
    return null;
  }
  const [scheme, value] = authorization.split(' ');
  if (scheme?.toLowerCase() !== 'bearer' || value === undefined || value.length === 0) {
    return null;
  }
  return value;
}

/**
 * Resolves a bearer API key to an {@link AuthContext}, or `null` if the key is
 * missing, malformed, unknown, or revoked. On success it also updates the row's
 * `last_used_at`. The admin client is used solely for this `api_keys` lookup.
 */
export async function resolveAuthContext(
  authorization: string | undefined,
  config: Config,
  adminClient: SupabaseClient,
): Promise<AuthContext | null> {
  const key = extractBearerKey(authorization);
  if (key === null || !isWellFormedApiKey(key)) {
    return null;
  }

  const keyHash = hashApiKey(key, config.apiKeyPepper);
  const { data, error } = await adminClient
    .from('api_keys')
    .select('id, user_id, key_hash')
    .eq('key_hash', keyHash)
    .eq('revoked', false)
    .limit(1)
    .maybeSingle<ApiKeyRow>();

  if (error !== null || data === null || !hashesEqual(data.key_hash, keyHash)) {
    return null;
  }

  await touchLastUsed(adminClient, data.id);
  const token = await mintUserToken(data.user_id, config.supabaseJwtSecret);
  return { userId: data.user_id, supabase: createUserClient(config, token) };
}

/** Stamps `last_used_at` on a successfully resolved key; failures are non-fatal. */
async function touchLastUsed(adminClient: SupabaseClient, id: string): Promise<void> {
  await adminClient
    .from('api_keys')
    .update({ last_used_at: new Date().toISOString() })
    .eq('id', id);
}
