/**
 * Integration tests for the rack-MCP write tools against the local Supabase
 * stack. Each test drives a real `McpServer` (built with a resolved
 * {@link AuthContext}) over an in-memory transport, so input is Zod-validated at
 * the boundary exactly as it would be over Streamable HTTP, and every write runs
 * under RLS as the resolved user.
 *
 * Covers the issue #13 acceptance: create/update/delete exist for plans, days,
 * exercises, and set logs; every create persists `source='agent'` and an
 * `updated_at`; an update advances `updated_at` and keeps `source='agent'`; a
 * delete is a hard delete; invalid input is rejected by Zod with no DB write;
 * and an update/delete targeting another user's row id is not-found with no
 * cross-user existence leak.
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
let dataClientA: SupabaseClient;
let clientB: SupabaseClient;
let treeB: SeededPlanTree;
let exerciseId: string;

beforeAll(async () => {
  userA = await createConfirmedUser(admin);
  userB = await createConfirmedUser(admin);
  keyA = newPlaintextKey();
  keyB = newPlaintextKey();
  await seedApiKey(admin, config, userA, keyA);
  await seedApiKey(admin, config, userB, keyB);
  dataClientA = await userClientFor(keyA);
  clientB = await userClientFor(keyB);
  treeB = await seedPlanTree(clientB, userB, `plan-${randomToken()}`);
  exerciseId = treeB.exerciseId;
});

afterAll(async () => {
  await deleteUser(admin, userA);
  await deleteUser(admin, userB);
});

/** Resolves a key to its user-scoped client for RLS-faithful test seeding. */
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

/** Parses the single JSON text block a write tool returns. */
function parsePayload(result: CallToolResult): Record<string, unknown> {
  const block = result.content[0];
  if (block === undefined || block.type !== 'text') {
    throw new Error('expected a text content block');
  }
  return JSON.parse(block.text) as Record<string, unknown>;
}

/**
 * Reads a single column for a row through a user-scoped client (RLS applies, so
 * the row must belong to that client's user). Service-role has no table grants on
 * the user tables, so reads go through the owner's client, as the read tests do.
 */
async function readColumn(
  client: SupabaseClient,
  table: string,
  id: string,
  column: string,
): Promise<unknown> {
  const { data } = await client
    .from(table)
    .select(column)
    .eq('id', id)
    .maybeSingle<Record<string, unknown>>();
  return data?.[column] ?? null;
}

describe('write tools', () => {
  it('exposes create/update/delete tools for every table', async () => {
    const client = await clientFor(keyA);
    const names = (await client.listTools()).tools.map((tool) => tool.name);
    expect(names).toEqual(
      expect.arrayContaining([
        'create_plans',
        'update_plans',
        'delete_plans',
        'create_plan_days',
        'update_plan_days',
        'delete_plan_days',
        'create_plan_exercises',
        'update_plan_exercises',
        'delete_plan_exercises',
        'create_set_logs',
        'update_set_logs',
        'delete_set_logs',
      ]),
    );
    expect(names).not.toContain('create_artifacts');
  });

  it('creates a plan with source=agent and a fresh updated_at', async () => {
    const client = await clientFor(keyA);
    const row = parsePayload(await call(client, 'create_plans', { name: `p-${randomToken()}` }));
    expect(row.source).toBe('agent');
    expect(typeof row.updated_at).toBe('string');
    expect(await readColumn(dataClientA, 'plans', row.id as string, 'source')).toBe('agent');
  });

  it('creates a full plan tree (day, exercise, set log) under the caller', async () => {
    const client = await clientFor(keyA);
    const plan = parsePayload(await call(client, 'create_plans', { name: `p-${randomToken()}` }));
    const day = parsePayload(
      await call(client, 'create_plan_days', { planId: plan.id, position: 0, tag: 'push' }),
    );
    expect(day.source).toBe('agent');
    const exercise = parsePayload(
      await call(client, 'create_plan_exercises', {
        dayId: day.id,
        exerciseId,
        position: 0,
        target: '3x8',
      }),
    );
    expect(exercise.source).toBe('agent');
    const log = parsePayload(
      await call(client, 'create_set_logs', {
        planExerciseId: exercise.id,
        date: '2026-06-17',
        weight: 60,
        reps: [8, 8, 8],
        rir: 2,
      }),
    );
    expect(log.source).toBe('agent');
    expect(log.reps).toEqual([8, 8, 8]);
  });

  it('updates a row, advancing updated_at and keeping source=agent', async () => {
    const client = await clientFor(keyA);
    const created = parsePayload(await call(client, 'create_plans', { name: 'before' }));
    const before = created.updated_at as string;
    await new Promise((resolve) => setTimeout(resolve, 5));
    const updated = parsePayload(
      await call(client, 'update_plans', { id: created.id, name: 'after' }),
    );
    expect(updated.name).toBe('after');
    expect(updated.source).toBe('agent');
    expect(new Date(updated.updated_at as string).getTime()).toBeGreaterThan(
      new Date(before).getTime(),
    );
  });

  it('hard-deletes a row the caller owns', async () => {
    const client = await clientFor(keyA);
    const created = parsePayload(await call(client, 'create_plans', { name: 'doomed' }));
    const result = await call(client, 'delete_plans', { id: created.id });
    expect(result.isError).toBeFalsy();
    expect(parsePayload(result).deleted).toBe(created.id);
    expect(await readColumn(dataClientA, 'plans', created.id as string, 'id')).toBeNull();
  });

  it('rejects malformed input with a structured error and no DB write', async () => {
    const client = await clientFor(keyA);
    const before = await dataClientA.from('set_logs').select('id');
    const result = await call(client, 'create_set_logs', {
      planExerciseId: 'not-a-uuid',
      reps: [-1],
    });
    expect(result.isError).toBe(true);
    const block = result.content[0];
    const message = block?.type === 'text' ? block.text : '';
    expect(message).toContain('planExerciseId');
    const after = await dataClientA.from('set_logs').select('id');
    expect(after.data?.length).toBe(before.data?.length);
  });

  it('rejects an empty update patch', async () => {
    const client = await clientFor(keyA);
    const created = parsePayload(await call(client, 'create_plans', { name: 'keep' }));
    const result = await call(client, 'update_plans', { id: created.id });
    expect(result.isError).toBe(true);
  });

  it('does not update another user\'s row (not found, no leak)', async () => {
    const client = await clientFor(keyA);
    const result = await call(client, 'update_plans', { id: treeB.planId, name: 'hijack' });
    expect(result.isError).toBe(true);
    const block = result.content[0];
    const message = block?.type === 'text' ? block.text : '';
    expect(message).toContain('not found');
    expect(await readColumn(clientB, 'plans', treeB.planId, 'source')).toBe('app');
  });

  it('does not delete another user\'s row (not found, no leak)', async () => {
    const client = await clientFor(keyA);
    const result = await call(client, 'delete_plans', { id: treeB.planId });
    expect(result.isError).toBe(true);
    expect(await readColumn(clientB, 'plans', treeB.planId, 'id')).toBe(treeB.planId);
  });
});
