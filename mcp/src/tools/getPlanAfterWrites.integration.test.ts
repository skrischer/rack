/**
 * Regression test for the §4.5 `get_plan`-after-write-burst hang.
 *
 * Faithfully models the stateless HTTP transport: every "request" resolves a
 * FRESH {@link AuthContext} (a brand-new per-request user-scoped supabase-js
 * client) and, for the read, a fresh `McpServer` — exactly as `http.ts` does
 * per inbound request. A burst of writes is sent, each through its own fresh
 * client, then a `get_plan` read runs (again through a fresh client/server) and
 * is timed. The read must return promptly rather than stall under a long
 * socket/connection timeout.
 */

import { Client } from '@modelcontextprotocol/sdk/client/index.js';
import { InMemoryTransport } from '@modelcontextprotocol/sdk/inMemory.js';
import type { CallToolResult } from '@modelcontextprotocol/sdk/types.js';
import type { SupabaseClient } from '@supabase/supabase-js';
import { afterAll, beforeAll, describe, expect, it } from 'vitest';

import { resolveAuthContext } from '../auth.js';
import type { AuthContext } from '../auth.js';
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

/** How many writes the burst fires before the timed read. */
const BURST = 80;
/** The read after the burst must return well under this bound. */
const READ_BUDGET_MS = 5_000;

let userId: string;
let key: string;
let planId: string;
let dayId: string;
let exerciseId: string;

/** Resolves the bearer key to a fresh per-request auth context (new client). */
async function freshAuth(): Promise<AuthContext> {
  const auth = await resolveAuthContext(`Bearer ${key}`, config, admin);
  if (auth === null) {
    throw new Error('expected a resolved auth context for the test key');
  }
  return auth;
}

/** A connected MCP client plus a closer that tears down both ends. */
interface FreshClient {
  client: Client;
  close: () => Promise<void>;
}

/** Builds a fresh server for a fresh auth context and connects a client to it. */
async function freshClient(): Promise<FreshClient> {
  const server = buildServer(await freshAuth());
  const [clientTransport, serverTransport] = InMemoryTransport.createLinkedPair();
  const client = new Client({ name: 'test-client', version: '0.0.0' });
  await Promise.all([client.connect(clientTransport), server.connect(serverTransport)]);
  return {
    client,
    close: async () => {
      await client.close();
      await server.close();
    },
  };
}

/** Parses the single JSON text block a read tool returns. */
function parsePayload(result: CallToolResult): unknown {
  const block = result.content[0];
  if (block === undefined || block.type !== 'text') {
    throw new Error('expected a text content block');
  }
  return JSON.parse(block.text);
}

beforeAll(async () => {
  userId = await createConfirmedUser(admin);
  key = newPlaintextKey();
  await seedApiKey(admin, config, userId, key);

  const setup = await freshAuth();
  const exercise = await setup.supabase
    .from('exercises')
    .select('id')
    .limit(1)
    .single<{ id: string }>();
  if (exercise.error !== null || exercise.data === null) {
    throw new Error(`no catalog exercise to seed against: ${exercise.error?.message ?? 'empty'}`);
  }
  exerciseId = exercise.data.id;

  const plan = await setup.supabase
    .from('plans')
    .insert({ user_id: userId, name: `plan-${randomToken()}`, kind: 'recomp', source: 'app' })
    .select('id')
    .single<{ id: string }>();
  if (plan.error !== null || plan.data === null) {
    throw new Error(`failed to seed plan: ${plan.error?.message ?? 'no row'}`);
  }
  planId = plan.data.id;

  const day = await setup.supabase
    .from('plan_days')
    .insert({ user_id: userId, plan_id: planId, position: 0, title: 'Day A', source: 'app' })
    .select('id')
    .single<{ id: string }>();
  if (day.error !== null || day.data === null) {
    throw new Error(`failed to seed day: ${day.error?.message ?? 'no row'}`);
  }
  dayId = day.data.id;
});

afterAll(async () => {
  await deleteUser(admin, userId);
});

describe('get_plan after a write burst', () => {
  it(
    'returns promptly after a burst of per-request writes',
    async () => {
      // Each iteration resolves a fresh auth context (a per-request user client
      // plus the api_keys lookup), exactly as the stateless HTTP transport does;
      // the generous test timeout covers that resolution as well as the inserts.
      for (let i = 0; i < BURST; i += 1) {
        const auth = await freshAuth();
        const { error } = await auth.supabase
          .from('plan_exercises')
          .insert({
            user_id: userId,
            day_id: dayId,
            exercise_id: exerciseId,
            position: i,
            source: 'agent',
          })
          .select('id')
          .single();
        if (error !== null) {
          throw new Error(`burst write ${i} failed: ${error.message}`);
        }
      }

      const { client, close } = await freshClient();
      try {
        const start = process.hrtime.bigint();
        const result = (await client.callTool({
          name: 'get_plan',
          arguments: { planId },
        })) as CallToolResult;
        const elapsedMs = Number(process.hrtime.bigint() - start) / 1e6;

        const tree = parsePayload(result) as {
          plan: { id: string } | null;
          exercises: unknown[];
        };
        expect(tree.plan?.id).toBe(planId);
        expect(tree.exercises).toHaveLength(BURST);
        expect(elapsedMs).toBeLessThan(READ_BUDGET_MS);
      } finally {
        await close();
      }
    },
    60_000,
  );
});
