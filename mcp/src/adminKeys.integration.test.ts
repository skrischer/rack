/**
 * Integration tests for the JWT-authenticated admin list + revoke endpoints
 * against the local Supabase stack. Covers the issue #32 acceptance: the list
 * returns only the caller's keys with non-secret display fields (never the
 * plaintext or hash); revoke soft-deletes a key the caller owns (sets `revoked`
 * and `revoked_at`) and a revoked key is rejected on its next tool call; both
 * endpoints resolve identity from the JWT alone, reject missing/invalid JWTs, and
 * never touch another user's keys (cross-user isolation). The inbound JWT is a
 * genuine ES256 token from the local Auth, verified via the project JWKS.
 */

import type { AddressInfo } from 'node:net';

import { Client } from '@modelcontextprotocol/sdk/client/index.js';
import { StreamableHTTPClientTransport } from '@modelcontextprotocol/sdk/client/streamableHttp.js';
import type { SupabaseClient } from '@supabase/supabase-js';
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

const config: Config = testConfig();
const admin: SupabaseClient = adminClient(config);
const httpServer = createHttpServer(config);

let baseUrl: string;
let userA: UserWithJwt;
let userB: UserWithJwt;

interface KeySummary {
  id: string;
  name: string | null;
  keyPrefix: string | null;
  createdAt: string;
  lastUsedAt: string | null;
  revoked: boolean;
}

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

function authHeader(user: UserWithJwt): Record<string, string> {
  return { Authorization: `Bearer ${user.accessToken}` };
}

async function mint(user: UserWithJwt, name: string): Promise<string> {
  const res = await fetch(`${baseUrl}/admin/keys`, {
    method: 'POST',
    headers: { 'content-type': 'application/json', ...authHeader(user) },
    body: JSON.stringify({ name }),
  });
  expect(res.status).toBe(201);
  return ((await res.json()) as { key: string }).key;
}

async function list(headers: Record<string, string>): Promise<Response> {
  return fetch(`${baseUrl}/admin/keys`, { method: 'GET', headers });
}

async function revoke(keyId: string, headers: Record<string, string>): Promise<Response> {
  return fetch(`${baseUrl}/admin/keys/${keyId}/revoke`, { method: 'POST', headers });
}

async function listKeys(user: UserWithJwt): Promise<KeySummary[]> {
  const res = await list(authHeader(user));
  expect(res.status).toBe(200);
  return ((await res.json()) as { keys: KeySummary[] }).keys;
}

describe('GET /admin/keys', () => {
  it('returns only the caller\'s keys with non-secret display fields', async () => {
    const plaintext = await mint(userA, 'laptop');
    const keys = await listKeys(userA);
    const named = keys.find((key) => key.name === 'laptop');
    expect(named).toBeDefined();
    expect(named?.revoked).toBe(false);
    expect(typeof named?.createdAt).toBe('string');
    expect(named).toHaveProperty('lastUsedAt');
    expect(named).toHaveProperty('keyPrefix');

    const serialized = JSON.stringify(keys);
    expect(serialized).not.toContain(plaintext);
    expect(serialized).not.toContain(hashApiKey(plaintext, config.apiKeyPepper));
    expect(serialized).not.toContain('key_hash');
  });

  it('scopes the list to the caller — A never sees B\'s keys, both directions', async () => {
    await mint(userA, 'a-only');
    await mint(userB, 'b-only');

    const aKeys = await listKeys(userA);
    const bKeys = await listKeys(userB);
    expect(aKeys.some((key) => key.name === 'a-only')).toBe(true);
    expect(aKeys.some((key) => key.name === 'b-only')).toBe(false);
    expect(bKeys.some((key) => key.name === 'b-only')).toBe(true);
    expect(bKeys.some((key) => key.name === 'a-only')).toBe(false);
  });

  it('rejects a missing JWT with 401', async () => {
    expect((await list({})).status).toBe(401);
  });

  it('rejects an invalid JWT with 401', async () => {
    expect((await list({ Authorization: 'Bearer not-a-jwt' })).status).toBe(401);
  });
});

describe('POST /admin/keys/{id}/revoke', () => {
  it('soft-deletes the caller\'s own key and a revoked key is rejected on its next tool call', async () => {
    const plaintext = await mint(userA, 'revoke-me');
    const before = await listKeys(userA);
    const target = before.find((key) => key.name === 'revoke-me');
    expect(target).toBeDefined();
    const keyId = target?.id ?? '';

    // The key works before revocation.
    const okClient = new Client({ name: 'revoke-test-pre', version: '0.0.0' });
    await okClient.connect(
      new StreamableHTTPClientTransport(new URL(`${baseUrl}/mcp`), {
        requestInit: { headers: { Authorization: `Bearer ${plaintext}` } },
      }),
    );
    await okClient.close();

    const res = await revoke(keyId, authHeader(userA));
    expect(res.status).toBe(200);
    expect((await res.json()) as { revoked: boolean }).toEqual({ revoked: true });

    // The row reflects the soft-delete: revoked true and a revoked_at stamp.
    const row = await admin
      .from('api_keys')
      .select('revoked, revoked_at')
      .eq('id', keyId)
      .single<{ revoked: boolean; revoked_at: string | null }>();
    expect(row.error).toBeNull();
    expect(row.data?.revoked).toBe(true);
    expect(row.data?.revoked_at).not.toBeNull();

    // The list now renders it as revoked.
    const after = await listKeys(userA);
    expect(after.find((key) => key.id === keyId)?.revoked).toBe(true);

    // The next tool call presenting the revoked key is rejected at auth.
    await expect(
      new Client({ name: 'revoke-test-post', version: '0.0.0' }).connect(
        new StreamableHTTPClientTransport(new URL(`${baseUrl}/mcp`), {
          requestInit: { headers: { Authorization: `Bearer ${plaintext}` } },
        }),
      ),
    ).rejects.toThrow();
  });

  it('targeting another user\'s key id mutates nothing and reports not-found', async () => {
    await mint(userB, 'b-protected');
    const bKey = (await listKeys(userB)).find((key) => key.name === 'b-protected');
    expect(bKey).toBeDefined();
    const bKeyId = bKey?.id ?? '';

    const res = await revoke(bKeyId, authHeader(userA));
    expect(res.status).toBe(404);

    // B's key is untouched: still present, not revoked.
    const row = await admin
      .from('api_keys')
      .select('revoked, revoked_at')
      .eq('id', bKeyId)
      .single<{ revoked: boolean; revoked_at: string | null }>();
    expect(row.error).toBeNull();
    expect(row.data?.revoked).toBe(false);
    expect(row.data?.revoked_at).toBeNull();
  });

  it('rejects a missing JWT with 401', async () => {
    const bKey = (await listKeys(userB))[0];
    expect((await revoke(bKey?.id ?? 'x', {})).status).toBe(401);
  });

  it('rejects an invalid JWT with 401', async () => {
    const bKey = (await listKeys(userB))[0];
    expect((await revoke(bKey?.id ?? 'x', { Authorization: 'Bearer not-a-jwt' })).status).toBe(401);
  });

  it('rejects a malformed (non-uuid) key id with 400', async () => {
    expect((await revoke('not-a-uuid', authHeader(userA))).status).toBe(400);
  });

  it('rejects a malformed percent-encoded key id with 400 without crashing the server', async () => {
    const res = await fetch(`${baseUrl}/admin/keys/%E0%A4%A/revoke`, {
      method: 'POST',
      headers: authHeader(userA),
    });
    expect(res.status).toBe(400);
    // The server is still serving: a well-formed request after it still works.
    expect((await list(authHeader(userA))).status).toBe(200);
  });
});
