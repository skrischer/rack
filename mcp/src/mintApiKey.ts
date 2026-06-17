/**
 * Mints new per-user API keys for the rack-MCP admin endpoint.
 *
 * Generates an opaque `rack_<random>` key, persists only its peppered SHA-256
 * hash (plus name + user_id + revoked=false) in `api_keys`, and returns the
 * plaintext exactly once. The write runs as the resolved user via a short-lived
 * user-scoped client, so it flows through RLS (`with check (user_id =
 * auth.uid())`) — never the service-role key, per the constitution (the caller is
 * already authenticated by their verified JWT). The plaintext is never logged or
 * persisted; only the hash is stored. See docs/specs/spec-rack-mcp.md.
 */

import { randomBytes } from 'node:crypto';

import { z } from 'zod';

import { API_KEY_PREFIX, hashApiKey } from './apiKey.js';
import type { Config } from './config.js';
import { createUserClient } from './supabase.js';
import { mintUserToken } from './userToken.js';

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
 * Generates a new API key for `userId`, stores only its peppered hash as that
 * user (under RLS, via a short-lived user-scoped client), and returns the
 * plaintext once. Throws if the row cannot be written so the caller can surface a
 * 500 without ever returning an unstored key.
 */
export async function mintApiKey(
  config: Config,
  userId: string,
  body: MintApiKeyBody,
): Promise<string> {
  const plaintext = generateApiKey();
  const token = await mintUserToken(userId, config.supabaseJwtSecret);
  const userClient = createUserClient(config, token);
  const { error } = await userClient.from('api_keys').insert({
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
