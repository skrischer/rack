/**
 * JWT-authenticated admin HTTP routes for the rack-MCP.
 *
 * Exposes the key lifecycle for the in-app surface: mint (`POST /admin/keys`),
 * list (`GET /admin/keys`), and revoke (`POST /admin/keys/{id}/revoke`). Every
 * route authenticates the caller with a genuine Supabase user JWT (verified
 * against the project's JWKS via asymmetric ES256, derived from `SUPABASE_URL`),
 * derives the user id solely from the token's `sub`, and acts as that resolved
 * user under RLS — never the service-role key, never a body `user_id`. Mint
 * returns the plaintext exactly once and persists only the peppered hash; list
 * returns non-secret display fields only; revoke is a soft-delete scoped to the
 * caller's own keys. Missing/invalid JWTs are rejected with 401 before any work
 * runs. See docs/specs/spec-api-key-management.md and docs/specs/spec-rack-mcp.md.
 */

import type { IncomingMessage, ServerResponse } from 'node:http';

import { listApiKeys, revokeApiKey, revokeApiKeyParamsSchema } from './adminKeys.js';
import { extractBearerKey } from './auth.js';
import type { Config } from './config.js';
import { mintApiKey, mintApiKeyBodySchema } from './mintApiKey.js';
import { verifyUserJwt, type SupabaseJwks } from './userToken.js';

/** Path the key-mint and key-list endpoints are served from. */
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
 * Resolves the caller's user id from the `Authorization` bearer JWT, verified
 * against the project JWKS. Returns the id on success; on a missing/invalid JWT
 * it writes a 401 and returns `null`, so callers can early-return.
 */
async function authenticate(
  req: IncomingMessage,
  res: ServerResponse,
  jwks: SupabaseJwks,
): Promise<string | null> {
  const token = extractBearerKey(req.headers.authorization);
  const userId = token === null ? null : await verifyUserJwt(token, jwks);
  if (userId === null) {
    sendJson(res, 401, { error: 'unauthorized' });
    return null;
  }
  return userId;
}

/**
 * Handles `POST /admin/keys`: verify the user JWT against the project JWKS,
 * validate the body, mint a key, and return the plaintext once together with its
 * non-secret display prefix. Rejects a missing/invalid JWT with 401, a bad body
 * with 400, and a storage failure with 500 (without returning a key).
 */
export async function handleMintApiKey(
  req: IncomingMessage,
  res: ServerResponse,
  config: Config,
  jwks: SupabaseJwks,
): Promise<void> {
  const userId = await authenticate(req, res, jwks);
  if (userId === null) {
    return;
  }
  const parsed = mintApiKeyBodySchema.safeParse(parseJsonBody(await readBody(req)));
  if (!parsed.success) {
    sendJson(res, 400, { error: 'invalid request body' });
    return;
  }
  const minted = await mintApiKey(config, userId, parsed.data);
  sendJson(res, 201, { key: minted.key, keyPrefix: minted.keyPrefix });
}

/**
 * Handles `GET /admin/keys`: verify the user JWT, then return the caller's own
 * keys as the resolved user under RLS — non-secret display fields only (never the
 * plaintext or hash). Rejects a missing/invalid JWT with 401; a read failure
 * surfaces as 500 via the router's catch.
 */
export async function handleListApiKeys(
  req: IncomingMessage,
  res: ServerResponse,
  config: Config,
  jwks: SupabaseJwks,
): Promise<void> {
  const userId = await authenticate(req, res, jwks);
  if (userId === null) {
    return;
  }
  const keys = await listApiKeys(config, userId);
  sendJson(res, 200, { keys });
}

/**
 * Handles `POST /admin/keys/{id}/revoke`: verify the user JWT, validate the key
 * id, then soft-delete that key as the resolved user under RLS. Returns 200 when
 * the caller's own key was revoked and 404 when no matching owned key exists (a
 * foreign id mutates nothing). Rejects a missing/invalid JWT with 401 and an
 * unparseable id with 400; identity comes only from the JWT.
 */
export async function handleRevokeApiKey(
  req: IncomingMessage,
  res: ServerResponse,
  config: Config,
  jwks: SupabaseJwks,
  keyId: string,
): Promise<void> {
  const userId = await authenticate(req, res, jwks);
  if (userId === null) {
    return;
  }
  const parsed = revokeApiKeyParamsSchema.safeParse({ id: keyId });
  if (!parsed.success) {
    sendJson(res, 400, { error: 'invalid key id' });
    return;
  }
  const revoked = await revokeApiKey(config, userId, parsed.data.id);
  if (!revoked) {
    sendJson(res, 404, { error: 'key not found' });
    return;
  }
  sendJson(res, 200, { revoked: true });
}
