/**
 * JWT-authenticated admin HTTP routes for the rack-MCP.
 *
 * Currently exposes the key-mint endpoint (`POST /admin/keys`): it authenticates
 * the caller with a genuine Supabase user JWT (verified against the project's
 * JWKS via asymmetric ES256, derived from `SUPABASE_URL`), derives the user id
 * solely from the token's `sub`, validates the request body with Zod (no
 * `user_id`/account selector accepted), mints a key, and returns the plaintext
 * exactly once. Only the peppered hash is persisted; the plaintext is never
 * logged. Missing/invalid JWTs are rejected with 401 before any mint runs. See
 * docs/specs/spec-rack-mcp.md.
 */

import type { IncomingMessage, ServerResponse } from 'node:http';

import { extractBearerKey } from './auth.js';
import type { Config } from './config.js';
import { mintApiKey, mintApiKeyBodySchema } from './mintApiKey.js';
import { verifyUserJwt, type SupabaseJwks } from './userToken.js';

/** Path the key-mint endpoint is served from. */
export const ADMIN_KEYS_PATH = '/admin/keys';

/** Largest mint request body accepted, guarding against oversized payloads. */
const MAX_BODY_BYTES = 4096;

/** Writes a JSON response with the given status; used for all admin replies. */
function sendJson(res: ServerResponse, status: number, payload: Record<string, unknown>): void {
  res.writeHead(status, { 'content-type': 'application/json' });
  res.end(JSON.stringify(payload));
}

/** Reads the request body as a UTF-8 string, rejecting if it exceeds the cap. */
async function readBody(req: IncomingMessage): Promise<string> {
  const chunks: Buffer[] = [];
  let size = 0;
  for await (const chunk of req) {
    size += (chunk as Buffer).length;
    if (size > MAX_BODY_BYTES) {
      throw new Error('request body too large');
    }
    chunks.push(chunk as Buffer);
  }
  return Buffer.concat(chunks).toString('utf8');
}

/** Parses the (possibly empty) JSON body, returning `undefined` on malformed JSON. */
function parseJsonBody(raw: string): unknown {
  if (raw.trim().length === 0) {
    return {};
  }
  try {
    return JSON.parse(raw) as unknown;
  } catch {
    return undefined;
  }
}

/**
 * Handles `POST /admin/keys`: verify the user JWT against the project JWKS,
 * validate the body, mint a key, and return the plaintext once. Rejects a
 * missing/invalid JWT with 401, a bad body with 400, and a storage failure with
 * 500 (without returning a key).
 */
export async function handleMintApiKey(
  req: IncomingMessage,
  res: ServerResponse,
  config: Config,
  jwks: SupabaseJwks,
): Promise<void> {
  if (req.method !== 'POST') {
    sendJson(res, 405, { error: 'method not allowed' });
    return;
  }
  const token = extractBearerKey(req.headers.authorization);
  const userId = token === null ? null : await verifyUserJwt(token, jwks);
  if (userId === null) {
    sendJson(res, 401, { error: 'unauthorized' });
    return;
  }
  const parsed = mintApiKeyBodySchema.safeParse(parseJsonBody(await readBody(req)));
  if (!parsed.success) {
    sendJson(res, 400, { error: 'invalid request body' });
    return;
  }
  const key = await mintApiKey(config, userId, parsed.data);
  sendJson(res, 201, { key });
}
