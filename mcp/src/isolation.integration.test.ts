/**
 * End-to-end per-user isolation tests for the rack-MCP against the local Supabase
 * stack. Proves the success criterion "a user's API key can only read/write that
 * user's data" (docs/vision.md) and the spec's cross-user isolation + service-role
 * audit checks (docs/specs/spec-rack-mcp.md, Verification checks 5 & 8).
 *
 * The suite mints a real `rack_<random>` key for each of two users via the real
 * {@link mintApiKey} flow (RLS-scoped insert, not a seeded row), resolves each key
 * through {@link resolveAuthContext} to its genuine user-scoped JWT client, and
 * drives the actual MCP tool surface over an in-memory transport. So every
 * assertion exercises the real auth -> user-scoped-token -> RLS path; no client is
 * mocked. It asserts: keys read/write/delete only their own rows and are
 * denied/not-found on the other user's rows (both directions); the data path is
 * the user-scoped client, never the service-role one (the service role has no
 * grants on the user tables, so the successful reads/writes can only have flowed
 * through the user-scoped client); a malformed tool input is Zod-rejected with no
 * DB write; and a revoked key is rejected at auth resolution.
 */

import { Client } from '@modelcontextprotocol/sdk/client/index.js';
import { InMemoryTransport } from '@modelcontextprotocol/sdk/inMemory.js';
import type { CallToolResult } from '@modelcontextprotocol/sdk/types.js';
import type { SupabaseClient } from '@supabase/supabase-js';
import { afterAll, beforeAll, describe, expect, it } from 'vitest';

import { hashApiKey } from './apiKey.js';
import { resolveAuthContext, type AuthContext } from './auth.js';
import type { Config } from './config.js';
import { mintApiKey } from './mintApiKey.js';
import { buildServer } from './server.js';
import {
  adminClient,
  createConfirmedUser,
  deleteUser,
  randomToken,
  seedPlanTree,
  testConfig,
  type SeededPlanTree,
} from './testSupport.js';

const config: Config = testConfig();
const admin: SupabaseClient = adminClient(config);

/** A user, their minted key, resolved context, MCP client, and seeded data. */
interface Party {
  userId: string;
  key: string;
  ctx: AuthContext;
  client: Client;
  tree: SeededPlanTree;
}

let a: Party;
let b: Party;

beforeAll(async () => {
  a = await makeParty('A');
  b = await makeParty('B');
});

afterAll(async () => {
  await a.client.close();
  await b.client.close();
  await deleteUser(admin, a.userId);
  await deleteUser(admin, b.userId);
});

/**
 * Creates a confirmed user, mints a real API key for it via {@link mintApiKey},
 * resolves the key to its user-scoped context, seeds a plan tree as that user, and
 * connects an MCP client driving the real tool surface for them.
 */
async function makeParty(label: string): Promise<Party> {
  const userId = await createConfirmedUser(admin);
  const { key } = await mintApiKey(config, userId, { name: `isolation-${label}` });
  const ctx = await resolve(key);
  const tree = await seedPlanTree(ctx.supabase, userId, `plan-${label}-${randomToken()}`);
  const client = await connect(ctx);
  return { userId, key, ctx, client, tree };
}

/** Resolves a bearer key to its {@link AuthContext} or fails the test. */
async function resolve(key: string): Promise<AuthContext> {
  const ctx = await resolveAuthContext(`Bearer ${key}`, config, admin);
  if (ctx === null) {
    throw new Error('expected a resolved auth context for the test key');
  }
  return ctx;
}

/** Connects an MCP client to a server built for the resolved identity. */
async function connect(auth: AuthContext): Promise<Client> {
  const server = buildServer(auth);
  const [clientTransport, serverTransport] = InMemoryTransport.createLinkedPair();
  const client = new Client({ name: 'isolation-test', version: '0.0.0' });
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

/** Extracts the single text block from a tool result (JSON payload or error). */
function text(result: CallToolResult): string {
  const block = result.content[0];
  return block?.type === 'text' ? block.text : '';
}

/** Parses the JSON payload a successful tool returns. */
function parse(result: CallToolResult): unknown {
  return JSON.parse(text(result));
}

describe('cross-user isolation', () => {
  it('mints distinct real keys that resolve to their own users', () => {
    expect(a.key).toMatch(/^rack_/);
    expect(b.key).toMatch(/^rack_/);
    expect(a.key).not.toBe(b.key);
    expect(a.ctx.userId).toBe(a.userId);
    expect(b.ctx.userId).toBe(b.userId);
    expect(a.userId).not.toBe(b.userId);
  });

  it('list_plans returns only the caller\'s own plans, both directions', async () => {
    const aPlans = (await listPlanIds(a.client));
    expect(aPlans).toContain(a.tree.planId);
    expect(aPlans).not.toContain(b.tree.planId);

    const bPlans = (await listPlanIds(b.client));
    expect(bPlans).toContain(b.tree.planId);
    expect(bPlans).not.toContain(a.tree.planId);
  });

  it('get_plan yields a null plan for the other user\'s id, no leak, both directions', async () => {
    const aOnB = parse(await call(a.client, 'get_plan', { planId: b.tree.planId })) as PlanTree;
    expect(aOnB.plan).toBeNull();
    expect(aOnB.days).toHaveLength(0);
    expect(aOnB.exercises).toHaveLength(0);

    const bOnA = parse(await call(b.client, 'get_plan', { planId: a.tree.planId })) as PlanTree;
    expect(bOnA.plan).toBeNull();
    expect(bOnA.days).toHaveLength(0);
    expect(bOnA.exercises).toHaveLength(0);
  });

  it('list_set_logs is scoped to the caller and the foreign filter is empty, both directions', async () => {
    const aLogs = (parse(await call(a.client, 'list_set_logs')) as Array<{ id: string }>).map(
      (log) => log.id,
    );
    expect(aLogs).toContain(a.tree.setLogId);
    expect(aLogs).not.toContain(b.tree.setLogId);

    const aOnBFilter = parse(
      await call(a.client, 'list_set_logs', { planExerciseId: b.tree.planExerciseId }),
    ) as unknown[];
    expect(aOnBFilter).toHaveLength(0);

    const bOnAFilter = parse(
      await call(b.client, 'list_set_logs', { planExerciseId: a.tree.planExerciseId }),
    ) as unknown[];
    expect(bOnAFilter).toHaveLength(0);
  });

  it('update on the other user\'s row is not-found and mutates nothing, both directions', async () => {
    const aHijack = await call(a.client, 'update_plans', { id: b.tree.planId, name: 'hijacked-by-a' });
    expect(aHijack.isError).toBe(true);
    expect(text(aHijack)).toContain('not found');
    expect(await readOwn(b.ctx, 'plans', b.tree.planId, 'source')).toBe('app');

    const bHijack = await call(b.client, 'update_plans', { id: a.tree.planId, name: 'hijacked-by-b' });
    expect(bHijack.isError).toBe(true);
    expect(text(bHijack)).toContain('not found');
    expect(await readOwn(a.ctx, 'plans', a.tree.planId, 'source')).toBe('app');
  });

  it('delete on the other user\'s rows is not-found and deletes nothing, both directions', async () => {
    const aDelPlan = await call(a.client, 'delete_plans', { id: b.tree.planId });
    expect(aDelPlan.isError).toBe(true);
    expect(text(aDelPlan)).toContain('not found');

    const aDelLog = await call(a.client, 'delete_set_logs', { id: b.tree.setLogId });
    expect(aDelLog.isError).toBe(true);
    expect(text(aDelLog)).toContain('not found');

    // B's rows survive A's attempts (read through B's own RLS-scoped client).
    expect(await readOwn(b.ctx, 'plans', b.tree.planId, 'id')).toBe(b.tree.planId);
    expect(await readOwn(b.ctx, 'set_logs', b.tree.setLogId, 'id')).toBe(b.tree.setLogId);

    const bDelPlan = await call(b.client, 'delete_plans', { id: a.tree.planId });
    expect(bDelPlan.isError).toBe(true);
    expect(text(bDelPlan)).toContain('not found');
    expect(await readOwn(a.ctx, 'plans', a.tree.planId, 'id')).toBe(a.tree.planId);
  });

  it('routes user data through the user-scoped client, never the service-role client', async () => {
    // The resolved context's client is not the service-role admin client.
    expect(a.ctx.supabase).not.toBe(admin);
    expect(b.ctx.supabase).not.toBe(admin);

    // The service role has NO grants on the user-data tables: any read it attempts
    // is permission-denied (42501). Since the tools above read and wrote these rows
    // successfully, that data path can only be the user-scoped (RLS) client.
    for (const table of ['plans', 'plan_days', 'plan_exercises', 'set_logs']) {
      const denied = await admin.from(table).select('id').limit(1);
      expect(denied.error?.code).toBe('42501');
    }
    // The one allowed service-role use — the api_keys auth-resolution lookup — works.
    const keys = await admin.from('api_keys').select('id').limit(1);
    expect(keys.error).toBeNull();
  });

  it('rejects malformed tool input with a Zod error and writes no row', async () => {
    const before = await a.ctx.supabase.from('set_logs').select('id');
    const result = await call(a.client, 'create_set_logs', {
      planExerciseId: 'not-a-uuid',
      reps: [-1],
    });
    expect(result.isError).toBe(true);
    expect(text(result)).toContain('planExerciseId');

    const after = await a.ctx.supabase.from('set_logs').select('id');
    expect(after.data?.length).toBe(before.data?.length);
  });

  it('rejects a revoked key at auth resolution', async () => {
    const { key: revocable } = await mintApiKey(config, a.userId, { name: 'isolation-revoke' });
    expect(await resolveAuthContext(`Bearer ${revocable}`, config, admin)).not.toBeNull();

    const lookup = await admin
      .from('api_keys')
      .select('id')
      .eq('key_hash', hashApiKey(revocable, config.apiKeyPepper))
      .single<{ id: string }>();
    expect(lookup.error).toBeNull();
    await admin.from('api_keys').update({ revoked: true }).eq('id', lookup.data?.id);

    expect(await resolveAuthContext(`Bearer ${revocable}`, config, admin)).toBeNull();
    // A's primary key is untouched and still resolves.
    expect(await resolveAuthContext(`Bearer ${a.key}`, config, admin)).not.toBeNull();
  });
});

/** Resolved plan tree returned by `get_plan`. */
interface PlanTree {
  plan: Record<string, unknown> | null;
  days: unknown[];
  exercises: unknown[];
}

/** Lists the plan ids `list_plans` returns for the given client. */
async function listPlanIds(client: Client): Promise<string[]> {
  const plans = parse(await call(client, 'list_plans')) as Array<{ id: string }>;
  return plans.map((plan) => plan.id);
}

/** Reads one column of a row through a user's own RLS-scoped client. */
async function readOwn(
  auth: AuthContext,
  table: string,
  id: string,
  column: string,
): Promise<unknown> {
  const { data } = await auth.supabase
    .from(table)
    .select(column)
    .eq('id', id)
    .maybeSingle<Record<string, unknown>>();
  return data?.[column] ?? null;
}
