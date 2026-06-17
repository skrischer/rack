/**
 * Cross-user isolation and revoked-key-rejection suite for the rack-MCP key
 * lifecycle, driving the real JWT-authenticated admin endpoints (mint, list,
 * revoke) over HTTP against the local Supabase stack. Covers the issue #34
 * acceptance end to end:
 *
 * - user A's JWT can neither list nor revoke user B's keys (no data returned,
 *   nothing mutated, both directions);
 * - create returns the plaintext exactly once plus its non-secret `key_prefix`,
 *   while the stored `api_keys` value is a peppered hash, never the plaintext;
 * - a key revoked through the admin endpoint (revoked = true, revoked_at set) is
 *   rejected on its next tool call;
 * - every admin endpoint rejects a missing / invalid / expired JWT and resolves
 *   identity from the token alone — a body `user_id` selector is never honored.
 *
 * The inbound user JWT is a genuine ES256 token from the local Auth, verified via
 * the project JWKS. The expired-JWT assertion exercises the production
 * {@link verifyUserJwt} against a local JWKS we control, so it proves the `exp`
 * branch deterministically without the Auth private key. See
 * docs/specs/spec-api-key-management.md.
 */

import type { AddressInfo } from 'node:net';

import { Client } from '@modelcontextprotocol/sdk/client/index.js';
import { StreamableHTTPClientTransport } from '@modelcontextprotocol/sdk/client/streamableHttp.js';
import type { SupabaseClient } from '@supabase/supabase-js';
import { createLocalJWKSet, exportJWK, generateKeyPair, SignJWT, type JWK } from 'jose';
import { afterAll, beforeAll, describe, expect, it } from 'vitest';

import { hashApiKey } from './apiKey.js';
import type { Config } from './config.js';
import { createHttpServer } from './http.js';
import {
  adminClient,
  createConfirmedUserWithJwt,
  deleteUser,
  testConfig,
  type UserWithJwt,
} from './testSupport.js';
import { verifyUserJwt } from './userToken.js';

const config: Config = testConfig();
const admin: SupabaseClient = adminClient(config);
const httpServer = createHttpServer(config);

/** A key as surfaced by the list endpoint — non-secret display fields only. */
interface KeySummary {
  id: string;
  name: string | null;
  keyPrefix: string | null;
  createdAt: string;
  lastUsedAt: string | null;
  revoked: boolean;
}

let baseUrl: string;
let userA: UserWithJwt;
let userB: UserWithJwt;

beforeAll(async () => {
  userA = await createConfirmedUserWithJwt(admin, config);
  userB = await createConfirmedUserWithJwt(admin, config);
  await new Promise<void>((resolve) => httpServer.listen(0, resolve));
  const { port } = httpServer.address() as AddressInfo;
  baseUrl = `http://127.0.0.1:${port}`;
});

afterAll(async () => {
  await deleteUser(admin, userA.userId);
  await deleteUser(admin, userB.userId);
  await new Promise<void>((resolve, reject) =>
    httpServer.close((err) => (err ? reject(err) : resolve())),
  );
});

function bearer(token: string): Record<string, string> {
  return { Authorization: `Bearer ${token}` };
}

/** Mints a key via the real endpoint and returns the create response payload. */
async function mint(user: UserWithJwt, body: unknown): Promise<Response> {
  return fetch(`${baseUrl}/admin/keys`, {
    method: 'POST',
    headers: { 'content-type': 'application/json', ...bearer(user.accessToken) },
    body: JSON.stringify(body),
  });
}

async function list(headers: Record<string, string>): Promise<Response> {
  return fetch(`${baseUrl}/admin/keys`, { method: 'GET', headers });
}

async function revoke(
  keyId: string,
  headers: Record<string, string>,
  body?: unknown,
): Promise<Response> {
  return fetch(`${baseUrl}/admin/keys/${keyId}/revoke`, {
    method: 'POST',
    headers: body === undefined ? headers : { 'content-type': 'application/json', ...headers },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
}

/** Lists a user's keys via the real endpoint, asserting a 200, newest first. */
async function listKeys(user: UserWithJwt): Promise<KeySummary[]> {
  const res = await list(bearer(user.accessToken));
  expect(res.status).toBe(200);
  return ((await res.json()) as { keys: KeySummary[] }).keys;
}

/** Connects an MCP client with the given bearer key; closes it on success. */
async function connectWithKey(key: string): Promise<void> {
  const client = new Client({ name: 'key-isolation-test', version: '0.0.0' });
  await client.connect(
    new StreamableHTTPClientTransport(new URL(`${baseUrl}/mcp`), {
      requestInit: { headers: bearer(key) },
    }),
  );
  await client.close();
}

describe('create returns the plaintext once and stores only a hash', () => {
  it('returns key + key_prefix and persists a hash, never the plaintext', async () => {
    const res = await mint(userA, { name: 'laptop' });
    expect(res.status).toBe(201);
    const { key, keyPrefix } = (await res.json()) as { key: string; keyPrefix: string };
    expect(key).toMatch(/^rack_/);
    expect(key.startsWith(keyPrefix)).toBe(true);
    expect(keyPrefix.length).toBeLessThan(key.length);

    const row = await admin
      .from('api_keys')
      .select('key_hash, key_prefix')
      .eq('key_hash', hashApiKey(key, config.apiKeyPepper))
      .single<{ key_hash: string; key_prefix: string }>();
    expect(row.error).toBeNull();
    expect(row.data?.key_hash).toBe(hashApiKey(key, config.apiKeyPepper));
    expect(row.data?.key_hash).not.toBe(key);
    expect(row.data?.key_prefix).toBe(keyPrefix);
  });

  it('never exposes the plaintext on a later list — only the prefix', async () => {
    const created = (await mint(userA, { name: 'desktop' })).json() as Promise<{
      key: string;
      keyPrefix: string;
    }>;
    const { key, keyPrefix } = await created;

    const serialized = JSON.stringify(await listKeys(userA));
    expect(serialized).not.toContain(key);
    expect(serialized).not.toContain(hashApiKey(key, config.apiKeyPepper));
    expect(serialized).toContain(keyPrefix);
  });
});

describe("cross-user isolation: A cannot list or revoke B's keys", () => {
  it("list returns only the caller's keys, both directions", async () => {
    await mint(userA, { name: 'a-only' });
    await mint(userB, { name: 'b-only' });

    const aKeys = await listKeys(userA);
    const bKeys = await listKeys(userB);
    expect(aKeys.some((key) => key.name === 'a-only')).toBe(true);
    expect(aKeys.some((key) => key.name === 'b-only')).toBe(false);
    expect(bKeys.some((key) => key.name === 'b-only')).toBe(true);
    expect(bKeys.some((key) => key.name === 'a-only')).toBe(false);
  });

  it("revoking B's key with A's JWT mutates nothing and reports not-found", async () => {
    await mint(userB, { name: 'b-protected' });
    const bKey = (await listKeys(userB)).find((key) => key.name === 'b-protected');
    expect(bKey).toBeDefined();
    const bKeyId = bKey?.id ?? '';

    const res = await revoke(bKeyId, bearer(userA.accessToken));
    expect(res.status).toBe(404);

    const row = await admin
      .from('api_keys')
      .select('revoked, revoked_at')
      .eq('id', bKeyId)
      .single<{ revoked: boolean; revoked_at: string | null }>();
    expect(row.error).toBeNull();
    expect(row.data?.revoked).toBe(false);
    expect(row.data?.revoked_at).toBeNull();
  });
});

describe('a revoked key is rejected on its next tool call', () => {
  it('soft-deletes via the admin endpoint and the key stops authenticating', async () => {
    const { key } = (await (await mint(userA, { name: 'revoke-me' })).json()) as { key: string };
    const target = (await listKeys(userA)).find((entry) => entry.name === 'revoke-me');
    expect(target).toBeDefined();
    const keyId = target?.id ?? '';

    // The key authenticates a tool call before revocation.
    await connectWithKey(key);

    const res = await revoke(keyId, bearer(userA.accessToken));
    expect(res.status).toBe(200);
    expect((await res.json()) as { revoked: boolean }).toEqual({ revoked: true });

    const row = await admin
      .from('api_keys')
      .select('revoked, revoked_at')
      .eq('id', keyId)
      .single<{ revoked: boolean; revoked_at: string | null }>();
    expect(row.error).toBeNull();
    expect(row.data?.revoked).toBe(true);
    expect(row.data?.revoked_at).not.toBeNull();

    // The next tool call presenting the revoked key is rejected at auth.
    await expect(connectWithKey(key)).rejects.toThrow();
  });
});

describe('admin endpoints reject missing / invalid / expired JWTs', () => {
  it('rejects a missing JWT with 401 on every endpoint', async () => {
    expect((await list({})).status).toBe(401);
    expect((await revoke('00000000-0000-0000-0000-000000000000', {})).status).toBe(401);
    const res = await fetch(`${baseUrl}/admin/keys`, {
      method: 'POST',
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify({ name: 'x' }),
    });
    expect(res.status).toBe(401);
  });

  it('rejects an invalid JWT with 401 on every endpoint', async () => {
    const bad = bearer('not-a-jwt');
    expect((await list(bad)).status).toBe(401);
    expect((await revoke('00000000-0000-0000-0000-000000000000', bad)).status).toBe(401);
    expect((await mintWithRawToken('not-a-jwt', { name: 'x' })).status).toBe(401);
  });

  it('verifyUserJwt rejects an expired token but accepts an unexpired one (same key)', async () => {
    const { jwks, sign } = await localJwks();
    const valid = await sign({ expiresIn: '5m' });
    const expired = await sign({ expiresIn: -60 });
    expect(await verifyUserJwt(valid, jwks)).toBe('expired-token-subject');
    expect(await verifyUserJwt(expired, jwks)).toBeNull();
  });
});

describe('identity comes only from the JWT — a body user_id is ignored', () => {
  it('mint rejects a body carrying a user_id selector (strict schema)', async () => {
    expect((await mint(userA, { name: 'x', user_id: userB.userId })).status).toBe(400);
  });

  it('revoke ignores a body user_id and stays scoped to the JWT caller', async () => {
    await mint(userB, { name: 'b-revoke-target' });
    const bKey = (await listKeys(userB)).find((key) => key.name === 'b-revoke-target');
    expect(bKey).toBeDefined();

    // A targets B's id AND supplies B's user_id in the body: still not-found,
    // because identity is the JWT's sub (A), never the body.
    const res = await revoke(bKey?.id ?? '', bearer(userA.accessToken), {
      user_id: userB.userId,
    });
    expect(res.status).toBe(404);

    const row = await admin
      .from('api_keys')
      .select('revoked')
      .eq('id', bKey?.id ?? '')
      .single<{ revoked: boolean }>();
    expect(row.data?.revoked).toBe(false);
  });
});

/** Mints with an arbitrary raw bearer token (for the invalid-JWT assertion). */
async function mintWithRawToken(token: string, body: unknown): Promise<Response> {
  return fetch(`${baseUrl}/admin/keys`, {
    method: 'POST',
    headers: { 'content-type': 'application/json', ...bearer(token) },
    body: JSON.stringify(body),
  });
}

/**
 * Builds a local ES256 JWKS plus a signer over its private key, so a token can be
 * signed with a key the JWKS contains — letting the expired-vs-valid assertion
 * exercise the production `exp` check rather than a signature mismatch.
 */
async function localJwks(): Promise<{
  jwks: ReturnType<typeof createLocalJWKSet>;
  sign: (opts: { expiresIn: string | number }) => Promise<string>;
}> {
  const { publicKey, privateKey } = await generateKeyPair('ES256');
  const publicJwk: JWK = { ...(await exportJWK(publicKey)), alg: 'ES256', use: 'sig' };
  const jwks = createLocalJWKSet({ keys: [publicJwk] });
  const sign = (opts: { expiresIn: string | number }): Promise<string> =>
    new SignJWT({ role: 'authenticated' })
      .setProtectedHeader({ alg: 'ES256', typ: 'JWT' })
      .setSubject('expired-token-subject')
      .setAudience('authenticated')
      .setIssuedAt()
      .setExpirationTime(opts.expiresIn)
      .sign(privateKey);
  return { jwks, sign };
}
