/**
 * Integration tests for the `list_artifacts` MCP tool against the local
 * Supabase stack. The tool is driven through a real `McpServer` over an
 * in-memory transport, so it runs under RLS as the resolved user (never
 * service-role), exactly as it would over Streamable HTTP.
 *
 * Covers the issue #41 acceptance: the tool takes no `user_id` (or any account
 * selector) and returns only the resolved user's rows; each row carries id,
 * name, type, storage_path, source, created_at, updated_at; rows are ordered
 * newest first; reads go through RLS so a cross-user attempt returns none of the
 * other user's rows. See docs/specs/spec-visualization-artifacts.md.
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

let userA: string;
let userB: string;
let keyA: string;
let keyB: string;

beforeAll(async () => {
  userA = await createConfirmedUser(admin);
  userB = await createConfirmedUser(admin);
  keyA = newPlaintextKey();
  keyB = newPlaintextKey();
  await seedApiKey(admin, config, userA, keyA);
  await seedApiKey(admin, config, userB, keyB);
});

afterAll(async () => {
  await deleteUser(admin, userA);
  await deleteUser(admin, userB);
});

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

/** Parses the JSON array body a list tool returns. */
function parseRows(result: CallToolResult): Record<string, unknown>[] {
  const block = result.content[0];
  if (block === undefined || block.type !== 'text') {
    throw new Error('expected a text content block');
  }
  return JSON.parse(block.text) as Record<string, unknown>[];
}

/** Creates an artifact for the given client and returns its id. */
async function seedArtifact(client: Client, name: string): Promise<string> {
  const row = parsePayload(
    await call(client, 'create_artifact', {
      name,
      type: 'text/html',
      content: '<html><body>chart</body></html>',
    }),
  );
  return row.id as string;
}

describe('list_artifacts', () => {
  it('exposes the list_artifacts tool', async () => {
    const client = await clientFor(keyA);
    const names = (await client.listTools()).tools.map((tool) => tool.name);
    expect(names).toContain('list_artifacts');
  });

  it('returns the resolved user rows with the expected shape, newest first', async () => {
    const client = await clientFor(keyA);
    const firstName = `first-${randomToken()}`;
    const secondName = `second-${randomToken()}`;
    await seedArtifact(client, firstName);
    const secondId = await seedArtifact(client, secondName);

    const rows = parseRows(await call(client, 'list_artifacts'));
    expect(rows.length).toBeGreaterThanOrEqual(2);

    const newest = rows[0];
    expect(newest?.id).toBe(secondId);
    expect(newest?.name).toBe(secondName);
    for (const column of [
      'id',
      'name',
      'type',
      'storage_path',
      'source',
      'created_at',
      'updated_at',
    ]) {
      expect(newest).toHaveProperty(column);
    }
    expect(newest?.source).toBe('agent');
  });

  it('isolates artifacts per user: A never sees B rows', async () => {
    const clientB = await clientFor(keyB);
    const bName = `b-only-${randomToken()}`;
    const bId = await seedArtifact(clientB, bName);

    const clientA = await clientFor(keyA);
    const rowsForA = parseRows(await call(clientA, 'list_artifacts'));
    expect(rowsForA.some((row) => row.id === bId)).toBe(false);
    expect(rowsForA.every((row) => row.name !== bName)).toBe(true);
  });

  it('rejects a caller-supplied user_id (strict schema)', async () => {
    const client = await clientFor(keyA);
    const result = await call(client, 'list_artifacts', {
      user_id: userB,
    });
    expect(result.isError).toBe(true);
  });
});
