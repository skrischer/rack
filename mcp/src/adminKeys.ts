/**
 * List and revoke operations for per-user rack-MCP API keys.
 *
 * Both run as the resolved user via a short-lived user-scoped client (minted
 * from the verified JWT's `sub`), so every read and write flows through RLS
 * (`user_id = auth.uid()`) — never the service-role key, per the constitution.
 * List returns only non-secret display fields (never the plaintext or the hash);
 * revoke is a soft-delete that sets `revoked = true` and `revoked_at = now()` on a
 * key the caller owns, leaving another user's keys untouched (RLS scopes the
 * update to the caller, so a foreign id matches no row). See
 * docs/specs/spec-api-key-management.md.
 */

import { z } from 'zod';

import type { Config } from './config.js';
import { createUserClient } from './supabase.js';
import { mintUserToken } from './userToken.js';

/** Columns the list endpoint exposes — non-secret display fields only. */
const LIST_COLUMNS = 'id, name, key_prefix, created_at, last_used_at, revoked' as const;

/** Zod schema for the revoke path parameter; a key id must be a UUID. */
export const revokeApiKeyParamsSchema = z.object({ id: z.string().uuid() }).strict();

/** A single API key as surfaced to the caller; never carries the secret or hash. */
export interface ApiKeySummary {
  id: string;
  name: string | null;
  keyPrefix: string | null;
  createdAt: string;
  lastUsedAt: string | null;
  revoked: boolean;
}

/** Raw `api_keys` row shape returned by the list query (snake_case columns). */
interface ApiKeyRow {
  id: string;
  name: string | null;
  key_prefix: string | null;
  created_at: string;
  last_used_at: string | null;
  revoked: boolean;
}

/** Maps a snake_case DB row to the camelCase summary returned to the caller. */
function toSummary(row: ApiKeyRow): ApiKeySummary {
  return {
    id: row.id,
    name: row.name,
    keyPrefix: row.key_prefix,
    createdAt: row.created_at,
    lastUsedAt: row.last_used_at,
    revoked: row.revoked,
  };
}

/**
 * Lists the caller's API keys as the resolved user under RLS, newest first.
 * Returns only non-secret fields; the plaintext and hash are never selected.
 * Throws if the read fails so the caller can surface a 500.
 */
export async function listApiKeys(config: Config, userId: string): Promise<ApiKeySummary[]> {
  const token = await mintUserToken(userId, config.supabaseJwtSecret);
  const userClient = createUserClient(config, token);
  const { data, error } = await userClient
    .from('api_keys')
    .select(LIST_COLUMNS)
    .order('created_at', { ascending: false })
    .returns<ApiKeyRow[]>();
  if (error !== null) {
    throw new Error(`failed to list api keys: ${error.message}`);
  }
  return (data ?? []).map(toSummary);
}

/**
 * Soft-deletes the caller's key with the given id as the resolved user under RLS:
 * sets `revoked = true` and `revoked_at = now()`. RLS scopes the update to the
 * caller, so targeting another user's id matches no row. Returns `true` when a row
 * was revoked, `false` when no matching (owned) row exists — letting the caller
 * report not-found/forbidden. Throws only on an unexpected DB error.
 */
export async function revokeApiKey(
  config: Config,
  userId: string,
  keyId: string,
): Promise<boolean> {
  const token = await mintUserToken(userId, config.supabaseJwtSecret);
  const userClient = createUserClient(config, token);
  const { data, error } = await userClient
    .from('api_keys')
    .update({ revoked: true, revoked_at: new Date().toISOString() })
    .eq('id', keyId)
    .eq('revoked', false)
    .select('id')
    .returns<Array<{ id: string }>>();
  if (error !== null) {
    throw new Error(`failed to revoke api key: ${error.message}`);
  }
  return (data ?? []).length > 0;
}
