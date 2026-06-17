/**
 * Integration tests for the `create_artifact` MCP tool against the local
 * Supabase stack. The tool is driven through a real `McpServer` over an
 * in-memory transport, so input is Zod-validated at the boundary exactly as it
 * would be over Streamable HTTP, and the upload + row insert both run under RLS
 * / Storage path policies as the resolved user (never service-role).
 *
 * Covers the issue #40 acceptance: input is Zod-validated (name, MIME enum,
 * 2 MB content cap, no caller `user_id`); the object lands at
 * `artifacts/{user_id}/{artifact_id}.{ext}` with the extension derived from the
 * MIME type; the row records `storage_path`, `type`, `name`, `source='agent'`,
 * `updated_at`; oversized or disallowed input is rejected with no row/object;
 * the tool returns the new id, name, type, and storage_path. See
 * docs/specs/spec-visualization-artifacts.md.
 */

import { Client } from '@modelcontextprotocol/sdk/client/index.js';
import { InMemoryTransport } from '@modelcontextprotocol/sdk/inMemory.js';
import type { CallToolResult } from '@modelcontextprotocol/sdk/types.js';
import type { SupabaseClient } from '@supabase/supabase-js';
import { afterAll, beforeAll, describe, expect, it } from 'vitest';

import { resolveAuthContext } from '../auth.js';
import type { Config } from '../config.js';
import { buildServer } from '../server.js';
import {
  adminClient,
  createConfirmedUser,
  deleteUser,
  newPlaintextKey,
  randomToken,
  seedApiKey,
  testConfig,
} from '../testSupport.js';

const config: Config = testConfig();
const admin: SupabaseClient = adminClient(config);

const BUCKET = 'artifacts';

let userA: string;
let keyA: string;
let dataClientA: SupabaseClient;

beforeAll(async () => {
  userA = await createConfirmedUser(admin);
  keyA = newPlaintextKey();
  await seedApiKey(admin, config, userA, keyA);
  dataClientA = await userClientFor(keyA);
});

afterAll(async () => {
  await deleteUser(admin, userA);
});

/** Resolves a key to its user-scoped client for RLS-faithful reads. */
async function userClientFor(key: string): Promise<SupabaseClient> {
  const auth = await resolveAuthContext(`Bearer ${key}`, config, admin);
  if (auth === null) {
    throw new Error('expected a resolved auth context for the test key');
  }
  return auth.supabase;
}

/** Connects a Client to a server built for the given bearer key's user. */
async function clientFor(key: string): Promise<Client> {
  const auth = await resolveAuthContext(`Bearer ${key}`, config, admin);
  if (auth === null) {
    throw new Error('expected a resolved auth context for the test key');
  }
  const server = buildServer(auth);
  const [clientTransport, serverTransport] = InMemoryTransport.createLinkedPair();
  const client = new Client({ name: 'test-client', version: '0.0.0' });
  await Promise.all([client.connect(clientTransport), server.connect(serverTransport)]);
  return client;
}

/** Calls a tool and returns its typed result. */
async function call(
  client: Client,
  name: string,
  args: Record<string, unknown> = {},
): Promise<CallToolResult> {
  return (await client.callTool({ name, arguments: args })) as CallToolResult;
}

/** Parses the single JSON text block a tool returns. */
function parsePayload(result: CallToolResult): Record<string, unknown> {
  const block = result.content[0];
  if (block === undefined || block.type !== 'text') {
    throw new Error('expected a text content block');
  }
  return JSON.parse(block.text) as Record<string, unknown>;
}

/** Extracts the text of a tool's first content block. */
function messageOf(result: CallToolResult): string {
  const block = result.content[0];
  return block?.type === 'text' ? block.text : '';
}

describe('create_artifact', () => {
  it('exposes the create_artifact tool', async () => {
    const client = await clientFor(keyA);
    const names = (await client.listTools()).tools.map((tool) => tool.name);
    expect(names).toContain('create_artifact');
  });

  it('uploads HTML, inserts the row, and returns id/name/type/storage_path', async () => {
    const client = await clientFor(keyA);
    const name = `chart-${randomToken()}`;
    const result = await call(client, 'create_artifact', {
      name,
      type: 'text/html',
      content: '<html><body>chart</body></html>',
    });
    expect(result.isError).toBeFalsy();
    const row = parsePayload(result);
    expect(typeof row.id).toBe('string');
    expect(row.name).toBe(name);
    expect(row.type).toBe('text/html');
    expect(row.storage_path).toBe(`${BUCKET}/${userA}/${row.id as string}.html`);

    const stored = await dataClientA
      .from('artifacts')
      .select('source, storage_path, type, name, updated_at')
      .eq('id', row.id as string)
      .single<Record<string, unknown>>();
    expect(stored.error).toBeNull();
    expect(stored.data?.source).toBe('agent');
    expect(stored.data?.storage_path).toBe(row.storage_path);
    expect(typeof stored.data?.updated_at).toBe('string');

    const objectPath = `${userA}/${row.id as string}.html`;
    const downloaded = await dataClientA.storage.from(BUCKET).download(objectPath);
    expect(downloaded.error).toBeNull();
    expect(await downloaded.data?.text()).toContain('chart');
  });

  it('derives the extension from the MIME enum (svg, png)', async () => {
    const client = await clientFor(keyA);
    const svg = parsePayload(
      await call(client, 'create_artifact', {
        name: `svg-${randomToken()}`,
        type: 'image/svg+xml',
        content: '<svg xmlns="http://www.w3.org/2000/svg"></svg>',
      }),
    );
    expect(svg.storage_path).toBe(`${BUCKET}/${userA}/${svg.id as string}.svg`);

    const png = parsePayload(
      await call(client, 'create_artifact', {
        name: `png-${randomToken()}`,
        type: 'image/png',
        content: 'pngbytes',
      }),
    );
    expect(png.storage_path).toBe(`${BUCKET}/${userA}/${png.id as string}.png`);
  });

  it('rejects a disallowed type with no row or object created', async () => {
    const client = await clientFor(keyA);
    const before = await dataClientA.from('artifacts').select('id');
    const result = await call(client, 'create_artifact', {
      name: `bad-${randomToken()}`,
      type: 'application/pdf',
      content: 'data',
    });
    expect(result.isError).toBe(true);
    expect(messageOf(result)).toContain('type');
    const after = await dataClientA.from('artifacts').select('id');
    expect(after.data?.length).toBe(before.data?.length);
  });

  it('rejects content over 2 MB with no row or object created', async () => {
    const client = await clientFor(keyA);
    const before = await dataClientA.from('artifacts').select('id');
    const result = await call(client, 'create_artifact', {
      name: `big-${randomToken()}`,
      type: 'text/html',
      content: 'a'.repeat(2 * 1024 * 1024 + 1),
    });
    expect(result.isError).toBe(true);
    expect(messageOf(result)).toContain('2 MB');
    const after = await dataClientA.from('artifacts').select('id');
    expect(after.data?.length).toBe(before.data?.length);
  });

  it('rejects a caller-supplied user_id (strict schema)', async () => {
    const client = await clientFor(keyA);
    const result = await call(client, 'create_artifact', {
      name: `strict-${randomToken()}`,
      type: 'text/html',
      content: '<html></html>',
      user_id: '00000000-0000-0000-0000-000000000000',
    });
    expect(result.isError).toBe(true);
  });
});
