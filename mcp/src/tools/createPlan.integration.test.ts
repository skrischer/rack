/**
 * Integration tests for the transactional nested-create tools (`create_plan`,
 * `create_plan_day`) against the local Supabase stack. Each test drives a real
 * `McpServer` over an in-memory transport, so the whole nested input is
 * Zod-validated at the boundary and the RPC runs under RLS as the resolved user.
 *
 * Covers the issue #197 acceptance: a 2-day multi-exercise tree writes in one
 * call and reads back via `get_plan` intact (typed prescription + grouping);
 * a mid-tree failure (invalid exerciseId) and a boundary failure (inverted range)
 * write NOTHING (atomic rollback) and return a loud error; `create_plan_day`
 * appends a day to an existing plan; and appending to another user's plan is a
 * not-found with no write.
 */

import { randomUUID } from 'node:crypto';

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
  CATALOG_FIXTURE_PREFIX,
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
let exerciseIds: string[];

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
  exerciseIds = await catalogExerciseIds(dataClientA, 2);
});

afterAll(async () => {
  await deleteUser(admin, userA);
  await deleteUser(admin, userB);
});

/** Resolves a key to its user-scoped client for RLS-faithful test setup. */
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

/** Reads `count` arbitrary non-fixture catalog exercise ids via a user client. */
async function catalogExerciseIds(client: SupabaseClient, count: number): Promise<string[]> {
  const { data, error } = await client
    .from('exercises')
    .select('id')
    .not('name', 'ilike', `${CATALOG_FIXTURE_PREFIX}%`)
    .limit(count)
    .overrideTypes<{ id: string }[], { merge: false }>();
  if (error !== null || data === null || data.length < count) {
    throw new Error(`need ${count} catalog exercises: ${error?.message ?? 'too few'}`);
  }
  return data.map((row) => row.id);
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

/** The error text of a tool result, or an empty string when it is not an error. */
function errorText(result: CallToolResult): string {
  const block = result.content[0];
  return block?.type === 'text' ? block.text : '';
}

/** An exercise row as returned flat by `get_plan`. */
interface ExerciseRow {
  day_id: string;
  position: number;
  sets: number | null;
  rep_min: number | null;
  rep_max: number | null;
  rir_low: number | null;
  rir_high: number | null;
  rest_seconds: number | null;
  cue: string | null;
  superset_id: number | null;
  group_type: string | null;
  source: string;
}

/** The plan tree as returned by `get_plan`. */
interface PlanTree {
  plan: { id: string; name: string; source: string } | null;
  days: { id: string; position: number; title: string | null; source: string }[];
  exercises: ExerciseRow[];
}

describe('create_plan', () => {
  it('exposes create_plan and create_plan_day tools', async () => {
    const client = await clientFor(keyA);
    const names = (await client.listTools()).tools.map((tool) => tool.name);
    expect(names).toEqual(expect.arrayContaining(['create_plan', 'create_plan_day']));
  });

  it('writes a 2-day, multi-exercise tree in one call and reads it back intact', async () => {
    const client = await clientFor(keyA);
    const name = `tree-${randomToken()}`;
    const [e1, e2] = exerciseIds;
    const created = parsePayload(
      await call(client, 'create_plan', {
        name,
        kind: 'recomp',
        days: [
          {
            position: 0,
            title: 'Push',
            tag: 'push',
            exercises: [
              {
                exerciseId: e1,
                position: 0,
                sets: 3,
                repMin: 6,
                repMax: 8,
                rirLow: 1,
                rirHigh: 2,
                restSeconds: 90,
                cue: 'brace hard',
                supersetId: 1,
                groupType: 'superset',
              },
              { exerciseId: e2, position: 1, sets: 3, repMin: 8, repMax: 12, supersetId: 1, groupType: 'superset' },
            ],
          },
          {
            position: 1,
            title: 'Pull',
            tag: 'pull',
            exercises: [{ exerciseId: e1, position: 0, sets: 4, repMin: 5, repMax: 5 }],
          },
        ],
      }),
    );
    expect(created).toMatchObject({ dayCount: 2, exerciseCount: 3 });
    const planId = created.planId as string;

    const tree = parsePayload(await call(client, 'get_plan', { planId })) as unknown as PlanTree;
    expect(tree.plan?.name).toBe(name);
    expect(tree.plan?.source).toBe('agent');
    expect(tree.days.map((day) => day.title)).toEqual(['Push', 'Pull']);
    expect(tree.days.every((day) => day.source === 'agent')).toBe(true);

    const dayPush = tree.days[0]?.id;
    const dayPull = tree.days[1]?.id;
    const pushExercises = tree.exercises.filter((ex) => ex.day_id === dayPush);
    const pullExercises = tree.exercises.filter((ex) => ex.day_id === dayPull);
    expect(pushExercises).toHaveLength(2);
    expect(pullExercises).toHaveLength(1);
    expect(tree.exercises.every((ex) => ex.source === 'agent')).toBe(true);
    expect(pushExercises[0]).toMatchObject({
      position: 0,
      sets: 3,
      rep_min: 6,
      rep_max: 8,
      rir_low: 1,
      rir_high: 2,
      rest_seconds: 90,
      cue: 'brace hard',
      superset_id: 1,
      group_type: 'superset',
    });
    expect(pushExercises.every((ex) => ex.superset_id === 1 && ex.group_type === 'superset')).toBe(
      true,
    );
    expect(pullExercises[0]).toMatchObject({ position: 0, sets: 4, rep_min: 5, rep_max: 5 });
  });

  it('rolls the whole tree back when an exercise id is invalid (atomic, loud error)', async () => {
    const client = await clientFor(keyA);
    const name = `rollback-${randomToken()}`;
    const result = await call(client, 'create_plan', {
      name,
      days: [
        {
          position: 0,
          exercises: [
            { exerciseId: exerciseIds[0], position: 0, sets: 3 },
            { exerciseId: randomUUID(), position: 1, sets: 3 },
          ],
        },
      ],
    });
    expect(result.isError).toBe(true);
    expect(errorText(result).length).toBeGreaterThan(0);
    const { data } = await dataClientA.from('plans').select('id').eq('name', name);
    expect(data ?? []).toHaveLength(0);
  });

  it('rejects an inverted range at the boundary (Zod) with no write', async () => {
    const client = await clientFor(keyA);
    const name = `zod-${randomToken()}`;
    const result = await call(client, 'create_plan', {
      name,
      days: [
        { position: 0, exercises: [{ exerciseId: exerciseIds[0], position: 0, repMin: 9, repMax: 6 }] },
      ],
    });
    expect(result.isError).toBe(true);
    expect(errorText(result)).toContain('repMin');
    const { data } = await dataClientA.from('plans').select('id').eq('name', name);
    expect(data ?? []).toHaveLength(0);
  });
});

describe('create_plan_day', () => {
  it('appends a day and its exercises to an existing plan', async () => {
    const client = await clientFor(keyA);
    const plan = parsePayload(
      await call(client, 'create_plan', { name: `base-${randomToken()}`, days: [] }),
    );
    const planId = plan.planId as string;
    const appended = parsePayload(
      await call(client, 'create_plan_day', {
        planId,
        position: 0,
        title: 'Legs',
        tag: 'legs',
        exercises: [
          { exerciseId: exerciseIds[0], position: 0, sets: 5, repMin: 5, repMax: 5 },
          { exerciseId: exerciseIds[1], position: 1, sets: 3 },
        ],
      }),
    );
    expect(appended).toMatchObject({ planId, exerciseCount: 2 });
    expect(typeof appended.dayId).toBe('string');

    const tree = parsePayload(await call(client, 'get_plan', { planId })) as unknown as PlanTree;
    expect(tree.days).toHaveLength(1);
    expect(tree.days[0]?.title).toBe('Legs');
    expect(tree.exercises.filter((ex) => ex.day_id === appended.dayId)).toHaveLength(2);
  });

  it('does not append to another user\'s plan (not found, no write)', async () => {
    const client = await clientFor(keyA);
    const result = await call(client, 'create_plan_day', {
      planId: treeB.planId,
      position: 1,
      exercises: [{ exerciseId: exerciseIds[0], position: 0 }],
    });
    expect(result.isError).toBe(true);
    expect(errorText(result)).toContain('not found');
    const { data } = await clientB
      .from('plan_days')
      .select('id')
      .eq('plan_id', treeB.planId);
    expect(data ?? []).toHaveLength(1); // only the seedPlanTree day remains
  });
});
