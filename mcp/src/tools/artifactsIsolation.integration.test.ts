/**
 * Cross-user isolation tests for the artifact tools against the local Supabase
 * stack, covering both the metadata table and the private Storage bucket. They
 * prove the spec's per-user isolation guarantee for artifacts
 * (docs/specs/spec-visualization-artifacts.md) and the success criterion that a
 * user's API key can only read/write that user's data (docs/vision.md).
 *
 * Two parties (A and B) each get a real `rack_<random>` key resolved to its
 * genuine user-scoped client, and the actual `create_artifact` / `list_artifacts`
 * tools are driven over an in-memory transport, so every assertion exercises the
 * real auth -> user-scoped-token -> RLS / Storage-policy path with no
 * service-role shortcut. The suite asserts: `create_artifact` as A only ever
 * writes under A's own `{user_id}/` prefix; B's `list_artifacts` never returns
 * A's row (table RLS); B cannot read A's object via a signed URL or a direct
 * download (Storage policy); and the service role itself has no read access to
 * the artifacts table or objects, so the successful own-reads can only have
 * flowed through the user-scoped client.
 */

import { Client } from '@modelcontextprotocol/sdk/client/index.js';
import { InMemoryTransport } from '@modelcontextprotocol/sdk/inMemory.js';
import type { CallToolResult } from '@modelcontextprotocol/sdk/types.js';
import type { SupabaseClient } from '@supabase/supabase-js';
import { afterAll, beforeAll, describe, expect, it } from 'vitest';

import { resolveAuthContext, type AuthContext } from '../auth.js';
import type { Config } from '../config.js';
import { mintApiKey } from '../mintApiKey.js';
import { buildServer } from '../server.js';
import {
  adminClient,
  createConfirmedUser,
  deleteUser,
  randomToken,
  testConfig,
} from '../testSupport.js';

const config: Config = testConfig();
const admin: SupabaseClient = adminClient(config);

const BUCKET = 'artifacts';

/** A user, their minted key, resolved user-scoped context, and MCP client. */
interface Party {
  userId: string;
  ctx: AuthContext;
  client: Client;
}

let a: Party;
let b: Party;
/** A's artifact id, storage_path, and object path, created via the tool as A. */
let aArtifact: { id: string; storagePath: string; objectPath: string };

beforeAll(async () => {
  a = await makeParty('A');
  b = await makeParty('B');
  aArtifact = await createHtmlArtifact(a, `chart-${randomToken()}`);
});

afterAll(async () => {
  await a.client.close();
  await b.client.close();
  await deleteUser(admin, a.userId);
  await deleteUser(admin, b.userId);
});

/**
 * Creates a confirmed user, mints a real API key for it via {@link mintApiKey},
 * resolves the key to its user-scoped context, and connects an MCP client
 * driving the real tool surface for that user.
 */
async function makeParty(label: string): Promise<Party> {
  const userId = await createConfirmedUser(admin);
  const { key } = await mintApiKey(config, userId, { name: `artifacts-iso-${label}` });
  const ctx = await resolveAuthContext(`Bearer ${key}`, config, admin);
  if (ctx === null) {
    throw new Error('expected a resolved auth context for the test key');
  }
  const client = await connect(ctx);
  return { userId, ctx, client };
}

/** Connects an MCP client to a server built for the resolved identity. */
async function connect(auth: AuthContext): Promise<Client> {
  const server = buildServer(auth);
  const [clientTransport, serverTransport] = InMemoryTransport.createLinkedPair();
  const client = new Client({ name: 'artifacts-iso-test', version: '0.0.0' });
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

/** Returns the single JSON text payload of a successful tool result. */
function payload(result: CallToolResult): string {
  const block = result.content[0];
  if (block === undefined || block.type !== 'text') {
    throw new Error('expected a text content block');
  }
  return block.text;
}

/** Parses a tool's JSON payload into an object (e.g. a created artifact row). */
function parseObject(result: CallToolResult): Record<string, unknown> {
  return JSON.parse(payload(result)) as Record<string, unknown>;
}

/** Parses a tool's JSON payload into a list of artifact rows by id. */
function parseRows(result: CallToolResult): Array<{ id: string }> {
  return JSON.parse(payload(result)) as Array<{ id: string }>;
}

/** Creates an HTML artifact for a party via the tool, returning its locators. */
async function createHtmlArtifact(
  party: Party,
  name: string,
): Promise<{ id: string; storagePath: string; objectPath: string }> {
  const result = await call(party.client, 'create_artifact', {
    name,
    type: 'text/html',
    content: '<html><body>chart</body></html>',
  });
  expect(result.isError).toBeFalsy();
  const row = parseObject(result);
  const id = row.id as string;
  const storagePath = row.storage_path as string;
  return { id, storagePath, objectPath: `${party.userId}/${id}.html` };
}

describe('cross-user isolation for artifacts (table + Storage)', () => {
  it('mints distinct keys that resolve to their own users', () => {
    expect(a.ctx.userId).toBe(a.userId);
    expect(b.ctx.userId).toBe(b.userId);
    expect(a.userId).not.toBe(b.userId);
  });

  it('create_artifact writes only under the caller\'s own {user_id}/ prefix', () => {
    expect(aArtifact.storagePath).toBe(`${BUCKET}/${aArtifact.objectPath}`);
    expect(aArtifact.objectPath.split('/')[0]).toBe(a.userId);
    expect(aArtifact.objectPath.startsWith(`${b.userId}/`)).toBe(false);
  });

  it('list_artifacts returns A\'s row to A but excludes it from B', async () => {
    const aRows = parseRows(await call(a.client, 'list_artifacts'));
    expect(aRows.map((row) => row.id)).toContain(aArtifact.id);

    const bRows = parseRows(await call(b.client, 'list_artifacts'));
    expect(bRows.map((row) => row.id)).not.toContain(aArtifact.id);
  });

  it('B cannot read A\'s artifact row directly through its own RLS client', async () => {
    const seenByB = await b.ctx.supabase
      .from('artifacts')
      .select('id')
      .eq('id', aArtifact.id)
      .maybeSingle<{ id: string }>();
    expect(seenByB.error).toBeNull();
    expect(seenByB.data).toBeNull();

    // A still sees its own row, proving the row exists and only B is blocked.
    const seenByA = await a.ctx.supabase
      .from('artifacts')
      .select('id')
      .eq('id', aArtifact.id)
      .maybeSingle<{ id: string }>();
    expect(seenByA.data?.id).toBe(aArtifact.id);
  });

  it('A can read its own object, but B cannot list, download, or sign A\'s object', async () => {
    const ownDownload = await a.ctx.supabase.storage.from(BUCKET).download(aArtifact.objectPath);
    expect(ownDownload.error).toBeNull();
    expect(await ownDownload.data?.text()).toContain('chart');

    const listedByB = await b.ctx.supabase.storage.from(BUCKET).list(a.userId);
    expect(listedByB.error).toBeNull();
    expect(listedByB.data ?? []).toEqual([]);

    const downloadByB = await b.ctx.supabase.storage.from(BUCKET).download(aArtifact.objectPath);
    expect(downloadByB.error).not.toBeNull();

    const signedByB = await b.ctx.supabase.storage
      .from(BUCKET)
      .createSignedUrl(aArtifact.objectPath, 60);
    expect(signedByB.error).not.toBeNull();
    expect(signedByB.data).toBeNull();
  });

  it('routes artifact access through the user-scoped client, never service-role', async () => {
    expect(a.ctx.supabase).not.toBe(admin);
    expect(b.ctx.supabase).not.toBe(admin);

    // The service role has no read grant on the artifacts table, so the own-reads
    // above can only have flowed through the user-scoped (RLS) client.
    const deniedRow = await admin.from('artifacts').select('id').limit(1);
    expect(deniedRow.error?.code).toBe('42501');
  });
});
