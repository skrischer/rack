/**
 * Integration tests for the Phase 17 `create_exercise` tool and the custom
 * indicator in `search_exercises` (issue #203) against the local Supabase stack.
 * Each test drives a real `McpServer` over an in-memory transport, so input is
 * Zod-validated at the boundary and writes/reads run through the user-scoped
 * client and RLS exactly as over Streamable HTTP.
 *
 * Covers the spec acceptance at the tool surface: `create_exercise` writes a
 * caller-owned row (user_id injected from auth, is_canonical false), a custom is
 * referenceable in a plan and reads back, `search_exercises` surfaces the
 * caller's own custom marked by `user_id`, another user's search never sees it,
 * anon sees catalog rows only, and deleting a custom referenced by a plan is
 * refused (FK RESTRICT). Cross-user read/update/delete at the raw RLS layer lives
 * in customExercisesRls.integration.test.ts. Requires `supabase start` (docker
 * container supabase_db_rack). See docs/specs/spec-custom-exercises.md.
 */

import { Client } from '@modelcontextprotocol/sdk/client/index.js';
import { InMemoryTransport } from '@modelcontextprotocol/sdk/inMemory.js';
import type { CallToolResult } from '@modelcontextprotocol/sdk/types.js';
import { createClient, type SupabaseClient } from '@supabase/supabase-js';
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
const anon: SupabaseClient = createClient(config.supabaseUrl, config.supabaseAnonKey, {
  auth: { autoRefreshToken: false, persistSession: false },
});

/** An exercise row as the tools project it (EXERCISE_COLUMNS). */
interface ExerciseRow {
  id: string;
  name: string;
  category: string | null;
  muscles: string[] | null;
  equipment: string[] | null;
  aliases: string[] | null;
  is_canonical: boolean;
  user_id: string | null;
}

let userA: string;
let userB: string;
let clientA: Client;
let clientB: Client;
let aRaw: SupabaseClient;

const token = randomToken();
const customName = `Custom Move ${token}`;
let customId: string;
let planId: string | undefined;

beforeAll(async () => {
  userA = await createConfirmedUser(admin);
  userB = await createConfirmedUser(admin);
  const keyA = newPlaintextKey();
  const keyB = newPlaintextKey();
  await seedApiKey(admin, config, userA, keyA);
  await seedApiKey(admin, config, userB, keyB);

  const authA = await resolveAuthContext(`Bearer ${keyA}`, config, admin);
  const authB = await resolveAuthContext(`Bearer ${keyB}`, config, admin);
  if (authA === null || authB === null) {
    throw new Error('expected resolved auth contexts for the test keys');
  }
  aRaw = authA.supabase;
  clientA = await connect(buildServer(authA));
  clientB = await connect(buildServer(authB));
});

afterAll(async () => {
  // Drop the plan first so its plan_exercise no longer references the custom;
  // otherwise the FK RESTRICT could block the ON DELETE CASCADE from auth.users.
  if (planId !== undefined) {
    await aRaw.from('plans').delete().eq('id', planId);
  }
  await deleteUser(admin, userA);
  await deleteUser(admin, userB);
});

/** Connects an in-memory client to the given server. */
async function connect(server: ReturnType<typeof buildServer>): Promise<Client> {
  const [clientTransport, serverTransport] = InMemoryTransport.createLinkedPair();
  const client = new Client({ name: 'test-client', version: '0.0.0' });
  await Promise.all([client.connect(clientTransport), server.connect(serverTransport)]);
  return client;
}

/** Calls a tool and returns its raw result. */
async function call(
  client: Client,
  name: string,
  args: Record<string, unknown> = {},
): Promise<CallToolResult> {
  return (await client.callTool({ name, arguments: args })) as CallToolResult;
}

/** Parses the single JSON text block a tool returns. */
function parse(result: CallToolResult): unknown {
  const block = result.content[0];
  if (block === undefined || block.type !== 'text') {
    throw new Error('expected a text content block');
  }
  return JSON.parse(block.text);
}

describe('create_exercise + custom-exercise search indicator', () => {
  it('writes a caller-owned custom row (user_id from auth, is_canonical false)', async () => {
    const row = parse(
      await call(clientA, 'create_exercise', {
        name: customName,
        category: 'Custom',
        equipment: ['Barbell'],
        primaryMuscles: ['Quads'],
        aliases: [`alias-${token}`],
      }),
    ) as ExerciseRow;
    customId = row.id;
    expect(row.id).toBeTypeOf('string');
    expect(row.name).toBe(customName);
    expect(row.user_id).toBe(userA);
    expect(row.is_canonical).toBe(false);
    expect(row.equipment).toContain('Barbell');
    expect(row.aliases).toContain(`alias-${token}`);
  });

  it('ignores a caller-supplied user_id and owns the row itself', async () => {
    const row = parse(
      await call(clientA, 'create_exercise', {
        name: `Spoof Attempt ${token}`,
        user_id: userB,
      }),
    ) as ExerciseRow;
    expect(row.user_id).toBe(userA);
    // Clean up this extra row so it does not linger (no FK references it).
    await aRaw.from('exercises').delete().eq('id', row.id);
  });

  it('rejects an empty name with a structured error naming the field', async () => {
    const result = await call(clientA, 'create_exercise', { name: '' });
    expect(result.isError).toBe(true);
    const block = result.content[0];
    const message = block?.type === 'text' ? block.text : '';
    expect(message).toContain('name');
  });

  it('lets a plan reference the custom exercise and reads it back', async () => {
    const created = parse(
      await call(clientA, 'create_plan', {
        name: `plan-${token}`,
        days: [{ position: 0, exercises: [{ exerciseId: customId, position: 0 }] }],
      }),
    ) as { planId: string; dayCount: number; exerciseCount: number };
    planId = created.planId;
    expect(created.exerciseCount).toBe(1);

    const tree = parse(await call(clientA, 'get_plan', { planId })) as {
      exercises: Array<{ exercise_id: string; exercise: { name: string } | null }>;
    };
    expect(tree.exercises).toHaveLength(1);
    expect(tree.exercises[0]?.exercise_id).toBe(customId);
    expect(tree.exercises[0]?.exercise?.name).toBe(customName);
  });

  it('surfaces the caller\'s own custom in search, marked by user_id', async () => {
    const rows = parse(await call(clientA, 'search_exercises', { q: token })) as ExerciseRow[];
    const mine = rows.find((r) => r.id === customId);
    expect(mine).toBeDefined();
    expect(mine?.user_id).toBe(userA);
  });

  it('marks shared catalog rows with a null user_id when browsing', async () => {
    const rows = parse(
      await call(clientA, 'search_exercises', { q: 'squat' }),
    ) as ExerciseRow[];
    expect(rows.length).toBeGreaterThan(0);
    expect(rows.every((r) => r.user_id === null)).toBe(true);
  });

  it('never surfaces user A\'s custom to user B', async () => {
    const rows = parse(await call(clientB, 'search_exercises', { q: token })) as ExerciseRow[];
    expect(rows.some((r) => r.id === customId)).toBe(false);
  });

  it('returns catalog rows only (user_id null) to anon', async () => {
    const { data, error } = await anon
      .rpc('search_exercises', { result_limit: 100 })
      .select('id, user_id');
    expect(error).toBeNull();
    const rows = (data ?? []) as Array<{ id: string; user_id: string | null }>;
    expect(rows.length).toBeGreaterThan(0);
    expect(rows.every((r) => r.user_id === null)).toBe(true);
    expect(rows.some((r) => r.id === customId)).toBe(false);
  });

  it('refuses to delete a custom exercise referenced by a plan (FK RESTRICT)', async () => {
    const { error } = await aRaw.from('exercises').delete().eq('id', customId);
    expect(error?.code).toBe('23503');

    // The custom survives the refused delete.
    const { data } = await aRaw
      .from('exercises')
      .select('id')
      .eq('id', customId)
      .maybeSingle<{ id: string }>();
    expect(data?.id).toBe(customId);
  });
});
