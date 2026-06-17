/**
 * Mints new per-user API keys for the rack-MCP admin endpoint.
 *
 * Generates an opaque `rack_<random>` key, persists only its peppered SHA-256
 * hash (plus name + user_id + revoked=false) in `api_keys` via the service-role
 * admin client — the only place the MCP writes `api_keys` — and returns the
 * plaintext exactly once. The plaintext is never logged or persisted; only the
 * hash is stored. See docs/specs/spec-rack-mcp.md.
 */

import { randomBytes } from 'node:crypto';

import type { SupabaseClient } from '@supabase/supabase-js';
import { z } from 'zod';

import { API_KEY_PREFIX, hashApiKey } from './apiKey.js';
import type { Config } from './config.js';

/** Bytes of entropy in the secret portion of a minted key. */
const KEY_SECRET_BYTES = 24;

/** Zod schema for the mint request body; no `user_id` or account selector accepted. */
export const mintApiKeyBodySchema = z
  .object({
    name: z.string().trim().min(1).max(100).optional(),
  })
  .strict();

/** Validated mint request body. */
export type MintApiKeyBody = z.infer<typeof mintApiKeyBodySchema>;

/** A freshly generated opaque key in the `rack_<random>` format. */
function generateApiKey(): string {
  return `${API_KEY_PREFIX}${randomBytes(KEY_SECRET_BYTES).toString('base64url')}`;
}

/**
 * Generates a new API key for `userId`, stores only its peppered hash, and
 * returns the plaintext once. Throws if the row cannot be written so the caller
 * can surface a 500 without ever returning an unstored key.
 */
export async function mintApiKey(
  adminClient: SupabaseClient,
  config: Config,
  userId: string,
  body: MintApiKeyBody,
): Promise<string> {
  const plaintext = generateApiKey();
  const { error } = await adminClient.from('api_keys').insert({
    user_id: userId,
    key_hash: hashApiKey(plaintext, config.apiKeyPepper),
    name: body.name ?? null,
    revoked: false,
  });
  if (error !== null) {
    throw new Error(`failed to store minted api key: ${error.message}`);
  }
  return plaintext;
}
