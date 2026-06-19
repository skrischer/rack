/**
 * RLS isolation tests for Phase 17 custom / user-authored exercises (issue #202)
 * against the local Supabase stack. Proves the migration
 * (`..._exercises_user_id_rls.sql`) gives the shared catalog an owner-scoped
 * custom-exercise model with NO cross-user leak:
 *  - the schema contract: a nullable `user_id` FK to auth.users (ON DELETE
 *    CASCADE), the partial own-row index, and FOUR DISTINCT per-action policies
 *    (not one `FOR ALL`) with the catalog-or-own SELECT predicate and
 *    `user_id = auth.uid()` write predicates;
 *  - the behaviour: anon sees catalog only (user_id IS NULL); an authenticated
 *    user reads the catalog plus its own custom; user B can neither see, update,
 *    nor delete user A's custom; and no user can write catalog rows (a NULL or
 *    spoofed `user_id` insert is rejected, a catalog update/delete affects no row).
 *
 * Each user gets a genuine user-scoped client via the real {@link mintApiKey} ->
 * {@link resolveAuthContext} path (the same harness as isolation.integration.test.ts),
 * so every assertion exercises the real auth -> user-scoped-token -> RLS path; the
 * service-role admin client is used for user setup/teardown only. Requires
 * `supabase start` (docker container supabase_db_rack). See
 * docs/specs/spec-custom-exercises.md.
 */

import { spawnSync } from 'node:child_process';

import { createClient, type SupabaseClient } from '@supabase/supabase-js';
import { afterAll, beforeAll, describe, expect, it } from 'vitest';

import { resolveAuthContext } from './auth.js';
import type { Config } from './config.js';
import { mintApiKey } from './mintApiKey.js';
import {
  adminClient,
  createConfirmedUser,
  deleteUser,
  randomToken,
  testConfig,
} from './testSupport.js';

const config: Config = testConfig();
const admin: SupabaseClient = adminClient(config);
const anon: SupabaseClient = createClient(config.supabaseUrl, config.supabaseAnonKey, {
  auth: { autoRefreshToken: false, persistSession: false },
});

const PSQL = (
  process.env.RACK_PSQL ?? 'docker exec -i supabase_db_rack psql -U postgres -d postgres'
)
  .split(' ')
  .filter(Boolean);

function runSql(sql: string): string {
  const [cmd, ...args] = PSQL;
  const res = spawnSync(cmd!, [...args, '-v', 'ON_ERROR_STOP=1', '-At', '-q'], {
    input: sql,
    encoding: 'utf8',
  });
  if (res.status !== 0) {
    throw new Error(`psql exited ${res.status}: ${res.stderr || res.stdout}`);
  }
  return res.stdout.trim();
}

/** A user plus the user-scoped client that RLS applies to. */
interface Party {
  userId: string;
  supabase: SupabaseClient;
}

let a: Party;
let b: Party;
let aCustomId: string;
const aCustomName = `custom-A-${randomToken()}`;

beforeAll(async () => {
  a = await makeParty('A');
  b = await makeParty('B');
  aCustomId = await insertCustom(a, aCustomName);
});

afterAll(async () => {
  // Deleting the auth user cascades to its custom exercises (ON DELETE CASCADE).
  await deleteUser(admin, a.userId);
  await deleteUser(admin, b.userId);
});

/** Creates a confirmed user and resolves a real key to its user-scoped client. */
async function makeParty(label: string): Promise<Party> {
  const userId = await createConfirmedUser(admin);
  const { key } = await mintApiKey(config, userId, { name: `custom-ex-${label}` });
  const ctx = await resolveAuthContext(`Bearer ${key}`, config, admin);
  if (ctx === null) {
    throw new Error('expected a resolved auth context for the test key');
  }
  return { userId, supabase: ctx.supabase };
}

/** Inserts a custom exercise owned by `party` and returns its id. */
async function insertCustom(party: Party, name: string): Promise<string> {
  const { data, error } = await party.supabase
    .from('exercises')
    .insert({ name, user_id: party.userId })
    .select('id')
    .single<{ id: string }>();
  if (error !== null || data === null) {
    throw new Error(`failed to insert custom exercise: ${error?.message ?? 'no row'}`);
  }
  return data.id;
}

/** Picks an arbitrary shared catalog row (user_id IS NULL) via the anon client. */
async function aCatalogRow(): Promise<{ id: string; name: string }> {
  const { data, error } = await anon
    .from('exercises')
    .select('id, name')
    .is('user_id', null)
    .limit(1)
    .single<{ id: string; name: string }>();
  if (error !== null || data === null) {
    throw new Error(`no catalog row available: ${error?.message ?? 'empty'}`);
  }
  return data;
}

describe('custom-exercises migration schema contract (local DB)', () => {
  it('adds user_id as a nullable uuid FK to auth.users with ON DELETE CASCADE', () => {
    const column = runSql(
      `select data_type || '/' || is_nullable from information_schema.columns
       where table_schema = 'public' and table_name = 'exercises' and column_name = 'user_id';`,
    );
    expect(column).toBe('uuid/YES');

    // confdeltype 'c' = ON DELETE CASCADE for the user_id foreign key.
    const onDelete = runSql(
      `select confdeltype from pg_constraint
       where conrelid = 'public.exercises'::regclass and contype = 'f'
         and (select attname from pg_attribute
              where attrelid = conrelid and attnum = conkey[1]) = 'user_id';`,
    );
    expect(onDelete).toBe('c');
  });

  it('creates the partial own-row index on (user_id) where user_id is not null', () => {
    const indexdef = runSql(
      `select indexdef from pg_indexes
       where schemaname = 'public' and tablename = 'exercises'
         and indexname = 'exercises_user_id_idx';`,
    );
    expect(indexdef).toContain('(user_id)');
    expect(indexdef).toContain('WHERE (user_id IS NOT NULL)');
  });

  it('uses four DISTINCT per-action policies, not a single FOR ALL', () => {
    const cmds = runSql(
      `select string_agg(cmd, ',' order by cmd) from pg_policies
       where schemaname = 'public' and tablename = 'exercises';`,
    );
    expect(cmds).toBe('DELETE,INSERT,SELECT,UPDATE');
  });

  it('keeps the SELECT policy as catalog-or-own for anon + authenticated', () => {
    const meta = runSql(
      `select cmd || '/' || array_to_string(array(select unnest(roles) order by 1), ',')
       from pg_policies
       where schemaname = 'public' and tablename = 'exercises'
         and policyname = 'exercises_public_read';`,
    );
    expect(meta).toBe('SELECT/anon,authenticated');

    const qual = runSql(
      `select qual from pg_policies
       where schemaname = 'public' and tablename = 'exercises'
         and policyname = 'exercises_public_read';`,
    );
    expect(qual).toContain('user_id IS NULL');
    expect(qual).toContain('auth.uid()');
  });

  it('scopes INSERT/UPDATE/DELETE policies to authenticated own rows', () => {
    const insert = runSql(
      `select array_to_string(array(select unnest(roles) order by 1), ',') || '/' || with_check
       from pg_policies
       where schemaname = 'public' and tablename = 'exercises'
         and policyname = 'exercises_insert_own';`,
    );
    expect(insert).toBe('authenticated/(user_id = auth.uid())');

    // UPDATE carries both USING (qual) and WITH CHECK.
    const update = runSql(
      `select qual || '|' || with_check from pg_policies
       where schemaname = 'public' and tablename = 'exercises'
         and policyname = 'exercises_update_own';`,
    );
    expect(update).toBe('(user_id = auth.uid())|(user_id = auth.uid())');

    const del = runSql(
      `select array_to_string(array(select unnest(roles) order by 1), ',') || '/' || qual
       from pg_policies
       where schemaname = 'public' and tablename = 'exercises'
         and policyname = 'exercises_delete_own';`,
    );
    expect(del).toBe('authenticated/(user_id = auth.uid())');
  });
});

describe('custom-exercises RLS behaviour', () => {
  it('lets the owner read back its own custom exercise', async () => {
    const { data, error } = await a.supabase
      .from('exercises')
      .select('id, name, user_id')
      .eq('id', aCustomId)
      .maybeSingle<{ id: string; name: string; user_id: string }>();
    expect(error).toBeNull();
    expect(data?.name).toBe(aCustomName);
    expect(data?.user_id).toBe(a.userId);
  });

  it('returns the shared catalog alongside the owner\'s own custom', async () => {
    const { data, error } = await a.supabase
      .from('exercises')
      .select('id, user_id')
      .or(`user_id.is.null,id.eq.${aCustomId}`);
    expect(error).toBeNull();
    const rows = data ?? [];
    expect(rows.some((r) => r.user_id === null)).toBe(true);
    expect(rows.some((r) => r.id === aCustomId)).toBe(true);
  });

  it('lets anon read catalog rows only, never a custom', async () => {
    const { data, error } = await anon.from('exercises').select('id, user_id');
    expect(error).toBeNull();
    const rows = data ?? [];
    expect(rows.length).toBeGreaterThan(0);
    expect(rows.every((r) => r.user_id === null)).toBe(true);
    expect(rows.some((r) => r.id === aCustomId)).toBe(false);

    const { data: byId } = await anon
      .from('exercises')
      .select('id')
      .eq('id', aCustomId)
      .maybeSingle<{ id: string }>();
    expect(byId).toBeNull();
  });

  it('hides user A\'s custom from user B', async () => {
    const { data } = await b.supabase
      .from('exercises')
      .select('id')
      .eq('id', aCustomId)
      .maybeSingle<{ id: string }>();
    expect(data).toBeNull();
  });

  it('refuses user B\'s update of user A\'s custom, leaving it unchanged', async () => {
    const { data: updated, error } = await b.supabase
      .from('exercises')
      .update({ name: 'hijacked-by-b' })
      .eq('id', aCustomId)
      .select('id');
    expect(error).toBeNull();
    expect(updated).toEqual([]);

    const { data: row } = await a.supabase
      .from('exercises')
      .select('name')
      .eq('id', aCustomId)
      .single<{ name: string }>();
    expect(row?.name).toBe(aCustomName);
  });

  it('refuses user B\'s delete of user A\'s custom, leaving it alive', async () => {
    const { data: deleted, error } = await b.supabase
      .from('exercises')
      .delete()
      .eq('id', aCustomId)
      .select('id');
    expect(error).toBeNull();
    expect(deleted).toEqual([]);

    const { data: row } = await a.supabase
      .from('exercises')
      .select('id')
      .eq('id', aCustomId)
      .maybeSingle<{ id: string }>();
    expect(row?.id).toBe(aCustomId);
  });

  it('rejects an authenticated insert of a catalog row (user_id NULL)', async () => {
    const { error } = await a.supabase
      .from('exercises')
      .insert({ name: `catalog-attempt-${randomToken()}`, user_id: null });
    expect(error?.code).toBe('42501');
  });

  it('rejects an authenticated insert spoofing another user\'s id', async () => {
    const { error } = await a.supabase
      .from('exercises')
      .insert({ name: `spoof-${randomToken()}`, user_id: b.userId });
    expect(error?.code).toBe('42501');
  });

  it('refuses an authenticated update of a catalog row, leaving it unchanged', async () => {
    const catalog = await aCatalogRow();
    const { data: updated, error } = await a.supabase
      .from('exercises')
      .update({ name: 'hijacked-catalog' })
      .eq('id', catalog.id)
      .select('id');
    expect(error).toBeNull();
    expect(updated).toEqual([]);

    const { data: after } = await anon
      .from('exercises')
      .select('name')
      .eq('id', catalog.id)
      .single<{ name: string }>();
    expect(after?.name).toBe(catalog.name);
  });

  it('refuses an authenticated delete of a catalog row', async () => {
    const catalog = await aCatalogRow();
    const { data: deleted, error } = await a.supabase
      .from('exercises')
      .delete()
      .eq('id', catalog.id)
      .select('id');
    expect(error).toBeNull();
    expect(deleted).toEqual([]);

    const { data: after } = await anon
      .from('exercises')
      .select('id')
      .eq('id', catalog.id)
      .maybeSingle<{ id: string }>();
    expect(after?.id).toBe(catalog.id);
  });
});
