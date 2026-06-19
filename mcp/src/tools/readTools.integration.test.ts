/**
 * Integration tests for the rack-MCP read tools against the local Supabase stack.
 *
 * Each test drives a real `McpServer` (built with a resolved {@link AuthContext})
 * over an in-memory transport, so input is Zod-validated at the boundary exactly
 * as it would be over Streamable HTTP. Covers the issue #12 acceptance: the four
 * read tools return only the caller's RLS-scoped rows (catalog search returns
 * public rows), and a malformed input yields a structured validation error
 * naming the field with no handler (and so no DB query) running.
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
  seedPlanTree,
  testConfig,
  type SeededPlanTree,
} from '../testSupport.js';

const config: Config = testConfig();
const admin: SupabaseClient = adminClient(config);

let userA: string;
let userB: string;
let keyA: string;
let keyB: string;
let treeA: SeededPlanTree;
let planNameA: string;

/** Resolves a key to its user-scoped client for RLS-faithful test seeding. */
async function userClientFor(key: string): Promise<SupabaseClient> {
  const auth = await resolveAuthContext(`Bearer ${key}`, config, admin);
  if (auth === null) {
    throw new Error('expected a resolved auth context for the test key');
  }
  return auth.supabase;
}

beforeAll(async () => {
  userA = await createConfirmedUser(admin);
  userB = await createConfirmedUser(admin);
  keyA = newPlaintextKey();
  keyB = newPlaintextKey();
  await seedApiKey(admin, config, userA, keyA);
  await seedApiKey(admin, config, userB, keyB);
  planNameA = `plan-${randomToken()}`;
  treeA = await seedPlanTree(await userClientFor(keyA), userA, planNameA);
  await seedPlanTree(await userClientFor(keyB), userB, `plan-${randomToken()}`);
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

/** Parses the single JSON text block a read tool returns. */
function parsePayload(result: CallToolResult): unknown {
  const block = result.content[0];
  if (block === undefined || block.type !== 'text') {
    throw new Error('expected a text content block');
  }
  return JSON.parse(block.text);
}

describe('read tools', () => {
  it('lists every read tool plus ping', async () => {
    const client = await clientFor(keyA);
    const names = (await client.listTools()).tools.map((tool) => tool.name);
    expect(names).toEqual(
      expect.arrayContaining([
        'ping',
        'list_plans',
        'get_plan',
        'list_set_logs',
        'search_exercises',
      ]),
    );
  });

  it('advertises readOnlyHint on read tools and not on write tools', async () => {
    const client = await clientFor(keyA);
    const byName = new Map((await client.listTools()).tools.map((tool) => [tool.name, tool]));

    const readTools = [
      'ping',
      'list_plans',
      'get_plan',
      'search_exercises',
      'list_set_logs',
      'list_artifacts',
    ];
    for (const name of readTools) {
      expect(byName.get(name)?.annotations?.readOnlyHint, name).toBe(true);
    }

    const writeTools = ['create_plans', 'update_plans', 'delete_plans', 'create_artifact'];
    for (const name of writeTools) {
      expect(byName.get(name)?.annotations?.readOnlyHint, name).toBeUndefined();
    }
  });

  it('gives every tool a non-empty description', async () => {
    const client = await clientFor(keyA);
    const tools = (await client.listTools()).tools;
    for (const tool of tools) {
      expect((tool.description ?? '').length, tool.name).toBeGreaterThan(0);
    }
  });

  it('list_plans returns only the caller\'s plans', async () => {
    const client = await clientFor(keyA);
    const plans = parsePayload(await call(client, 'list_plans')) as Array<{
      id: string;
      name: string;
    }>;
    expect(plans).toHaveLength(1);
    expect(plans[0]?.id).toBe(treeA.planId);
    expect(plans[0]?.name).toBe(planNameA);
  });

  it('get_plan returns the plan with its days and exercises', async () => {
    const client = await clientFor(keyA);
    const tree = parsePayload(await call(client, 'get_plan', { planId: treeA.planId })) as {
      plan: { id: string } | null;
      days: Array<{ id: string }>;
      exercises: Array<{ id: string; exercise: { name: string } | null }>;
    };
    expect(tree.plan?.id).toBe(treeA.planId);
    expect(tree.days.map((day) => day.id)).toEqual([treeA.dayId]);
    expect(tree.exercises.map((ex) => ex.id)).toEqual([treeA.planExerciseId]);
    expect(tree.exercises[0]?.exercise?.name).toBeTypeOf('string');
  });

  it('get_plan returns a null plan for another user\'s plan id (no leak)', async () => {
    const client = await clientFor(keyA);
    const otherTree = await seedPlanTree(await userClientFor(keyB), userB, `plan-${randomToken()}`);
    const tree = parsePayload(await call(client, 'get_plan', { planId: otherTree.planId })) as {
      plan: unknown;
      days: unknown[];
      exercises: unknown[];
    };
    expect(tree.plan).toBeNull();
    expect(tree.days).toHaveLength(0);
    expect(tree.exercises).toHaveLength(0);
  });

  it('list_set_logs returns the caller\'s logs and honors the exercise filter', async () => {
    const client = await clientFor(keyA);
    const all = parsePayload(await call(client, 'list_set_logs')) as Array<{ id: string }>;
    expect(all.map((log) => log.id)).toContain(treeA.setLogId);

    const filtered = parsePayload(
      await call(client, 'list_set_logs', { planExerciseId: treeA.planExerciseId }),
    ) as Array<{ id: string; plan_exercise_id: string }>;
    expect(filtered).toHaveLength(1);
    expect(filtered[0]?.id).toBe(treeA.setLogId);
  });

  it('list_set_logs honors the date range filter', async () => {
    const client = await clientFor(keyA);
    const before = parsePayload(
      await call(client, 'list_set_logs', { from: '2026-05-01', to: '2026-05-31' }),
    ) as unknown[];
    expect(before).toHaveLength(0);

    const within = parsePayload(
      await call(client, 'list_set_logs', { from: '2026-06-01', to: '2026-06-30' }),
    ) as Array<{ id: string }>;
    expect(within.map((log) => log.id)).toContain(treeA.setLogId);
  });

  it('search_exercises returns ranked public catalog rows, best match first', async () => {
    const client = await clientFor(keyA);
    const rows = parsePayload(await call(client, 'search_exercises', { q: 'squat' })) as Array<{
      id: string;
      name: string;
    }>;
    expect(rows.length).toBeGreaterThan(0);
    // Trigram word-similarity matches name + aliases, so an alias-only row may
    // appear; the top-ranked row for a plain single token is an exact name match.
    expect(rows[0]?.name.toLowerCase()).toContain('squat');
  });

  it('rejects malformed input with a structured error naming the field', async () => {
    const client = await clientFor(keyA);
    const result = await call(client, 'get_plan', { planId: 'not-a-uuid' });
    expect(result.isError).toBe(true);
    const block = result.content[0];
    const message = block?.type === 'text' ? block.text : '';
    expect(message).toContain('planId');
  });

  it('browses the catalog when search_exercises is called with no q', async () => {
    const client = await clientFor(keyA);
    const rows = parsePayload(await call(client, 'search_exercises', {})) as unknown[];
    expect(rows.length).toBeGreaterThan(0);
  });

  it('rejects a non-string search term naming the field', async () => {
    const client = await clientFor(keyA);
    const result = await call(client, 'search_exercises', { q: 123 });
    expect(result.isError).toBe(true);
    const block = result.content[0];
    const message = block?.type === 'text' ? block.text : '';
    expect(message).toContain('q');
  });
});
