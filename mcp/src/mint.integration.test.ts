/**
 * Integration tests for the JWT-authenticated key-mint endpoint against the local
 * Supabase stack. Covers the issue #14 acceptance: a successful mint returns the
 * plaintext once and stores only its peppered hash, a missing/invalid JWT is
 * rejected with 401, the plaintext never appears in the DB row, and a minted key
 * immediately authenticates against the tool endpoint for that user only. The
 * inbound user JWT is a genuine ES256 token from the local Auth, verified via the
 * project JWKS.
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

let mintUrl: URL;
let mcpUrl: URL;
let userA: UserWithJwt;

beforeAll(async () => {
  userA = await createConfirmedUserWithJwt(admin, config);
  await new Promise<void>((resolve) => httpServer.listen(0, resolve));
  const { port } = httpServer.address() as AddressInfo;
  mintUrl = new URL(`http://127.0.0.1:${port}/admin/keys`);
  mcpUrl = new URL(`http://127.0.0.1:${port}/mcp`);
});

afterAll(async () => {
  await deleteUser(admin, userA.userId);
  await new Promise<void>((resolve, reject) =>
    httpServer.close((err) => (err ? reject(err) : resolve())),
  );
});

async function mint(headers: Record<string, string>, body?: unknown): Promise<Response> {
  return fetch(mintUrl, {
    method: 'POST',
    headers: { 'content-type': 'application/json', ...headers },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
}

describe('POST /admin/keys', () => {
  it('mints a key for the JWT user and stores only its hash', async () => {
    const res = await mint({ Authorization: `Bearer ${userA.accessToken}` }, { name: 'laptop' });
    expect(res.status).toBe(201);
    const { key, keyPrefix } = (await res.json()) as { key: string; keyPrefix: string };
    expect(key).toMatch(/^rack_/);
    expect(key.startsWith(keyPrefix)).toBe(true);
    expect(keyPrefix.length).toBeLessThan(key.length);

    const row = await admin
      .from('api_keys')
      .select('key_hash, key_prefix, name, user_id, revoked')
      .eq('key_hash', hashApiKey(key, config.apiKeyPepper))
      .single<{
        key_hash: string;
        key_prefix: string;
        name: string;
        user_id: string;
        revoked: boolean;
      }>();
    expect(row.error).toBeNull();
    expect(row.data?.user_id).toBe(userA.userId);
    expect(row.data?.name).toBe('laptop');
    expect(row.data?.revoked).toBe(false);
    expect(row.data?.key_prefix).toBe(keyPrefix);
    expect(row.data?.key_hash).not.toContain(key);
  });

  it('never stores the plaintext key in any api_keys row', async () => {
    const res = await mint({ Authorization: `Bearer ${userA.accessToken}` }, { name: 'desktop' });
    const { key } = (await res.json()) as { key: string };

    const all = await admin
      .from('api_keys')
      .select('key_hash, key_prefix, name')
      .eq('user_id', userA.userId);
    expect(all.error).toBeNull();
    for (const row of all.data ?? []) {
      expect(row.key_hash).not.toBe(key);
      expect(row.key_hash).not.toContain(key);
      expect(row.name).not.toBe(key);
      expect(key).not.toBe(row.key_prefix);
    }
  });

  it('rejects a request with no JWT', async () => {
    expect((await mint({}, { name: 'laptop' })).status).toBe(401);
  });

  it('rejects a request with an invalid JWT', async () => {
    expect((await mint({ Authorization: 'Bearer not-a-jwt' }, { name: 'laptop' })).status).toBe(401);
  });

  it('rejects a body carrying a user_id selector', async () => {
    const res = await mint(
      { Authorization: `Bearer ${userA.accessToken}` },
      { name: 'laptop', user_id: 'other' },
    );
    expect(res.status).toBe(400);
  });

  it('rejects a request with no name', async () => {
    const res = await mint({ Authorization: `Bearer ${userA.accessToken}` }, {});
    expect(res.status).toBe(400);
  });

  it('lets a freshly minted key authenticate against the tool endpoint for its user', async () => {
    const res = await mint({ Authorization: `Bearer ${userA.accessToken}` }, { name: 'cli' });
    const { key } = (await res.json()) as { key: string };

    const client = new Client({ name: 'rack-mcp-mint-test', version: '0.0.0' });
    await client.connect(
      new StreamableHTTPClientTransport(mcpUrl, {
        requestInit: { headers: { Authorization: `Bearer ${key}` } },
      }),
    );
    const { tools } = await client.listTools();
    expect(tools.map((tool) => tool.name)).toContain('ping');
    await client.close();
  });
});
