/**
 * Shared helpers for the rack-MCP integration tests, run against the local
 * Supabase stack (`supabase start`). The service-role admin API is used here for
 * TEST SETUP only (creating confirmed users, seeding `api_keys` rows); it is
 * never used by the MCP itself for user data. Every fixture uses a random suffix
 * so repeated or parallel runs do not collide.
 */

import { existsSync } from 'node:fs';
import { randomBytes } from 'node:crypto';

import { createClient, type SupabaseClient } from '@supabase/supabase-js';

import { hashApiKey, API_KEY_PREFIX } from './apiKey.js';
import { loadConfig, type Config } from './config.js';

if (existsSync('.env')) {
  process.loadEnvFile('.env');
}

/** Loads the validated config from the (now populated) test environment. */
export function testConfig(): Config {
  return loadConfig();
}

/** A service-role admin client for fixture setup/teardown only. */
export function adminClient(config: Config): SupabaseClient {
  return createClient(config.supabaseUrl, config.supabaseServiceRoleKey, {
    auth: { autoRefreshToken: false, persistSession: false },
  });
}

/** A short random token for unique fixture identifiers. */
export function randomToken(): string {
  return randomBytes(8).toString('hex');
}

/**
 * Name prefix reserved for transient catalog fixtures a test inserts into the
 * shared `public.exercises` table. `seedPlanTree` excludes these so a concurrent
 * test never FK-references a fixture another test is about to delete; any test
 * adding catalog rows MUST name them with this prefix.
 */
export const CATALOG_FIXTURE_PREFIX = 'ZZZ Catalog Fixture';

/** Creates an email-confirmed Auth user and returns its id. */
export async function createConfirmedUser(admin: SupabaseClient): Promise<string> {
  const email = `rack-mcp-test-${randomToken()}@example.com`;
  const { data, error } = await admin.auth.admin.createUser({
    email,
    password: `pw-${randomToken()}`,
    email_confirm: true,
  });
  if (error !== null || data.user === null) {
    throw new Error(`failed to create test user: ${error?.message ?? 'no user returned'}`);
  }
  return data.user.id;
}

/** A confirmed Auth user plus a real Supabase access token (user JWT) for it. */
export interface UserWithJwt {
  userId: string;
  accessToken: string;
}

/**
 * Creates an email-confirmed Auth user and signs it in to obtain a genuine
 * Supabase user JWT (ES256, signed by the local Auth's JWKS key), mirroring what
 * the app holds when it calls the mint endpoint. The sign-in uses the anon
 * client, exactly like a real client would.
 */
export async function createConfirmedUserWithJwt(
  admin: SupabaseClient,
  config: Config,
): Promise<UserWithJwt> {
  const email = `rack-mcp-test-${randomToken()}@example.com`;
  const password = `pw-${randomToken()}`;
  const created = await admin.auth.admin.createUser({ email, password, email_confirm: true });
  if (created.error !== null || created.data.user === null) {
    throw new Error(`failed to create test user: ${created.error?.message ?? 'no user'}`);
  }
  const anon = createClient(config.supabaseUrl, config.supabaseAnonKey, {
    auth: { autoRefreshToken: false, persistSession: false },
  });
  const signedIn = await anon.auth.signInWithPassword({ email, password });
  if (signedIn.error !== null || signedIn.data.session === null) {
    throw new Error(`failed to sign in test user: ${signedIn.error?.message ?? 'no session'}`);
  }
  return { userId: created.data.user.id, accessToken: signedIn.data.session.access_token };
}

/** A freshly minted plaintext API key in the `rack_<random>` format. */
export function newPlaintextKey(): string {
  return `${API_KEY_PREFIX}${randomToken()}${randomToken()}`;
}

/**
 * Seeds an `api_keys` row for the given user with the peppered hash of `key`,
 * returning the row id. Uses the admin client to bypass RLS for setup.
 */
export async function seedApiKey(
  admin: SupabaseClient,
  config: Config,
  userId: string,
  key: string,
  options: { revoked?: boolean } = {},
): Promise<string> {
  const { data, error } = await admin
    .from('api_keys')
    .insert({
      user_id: userId,
      key_hash: hashApiKey(key, config.apiKeyPepper),
      name: 'integration-test',
      revoked: options.revoked ?? false,
    })
    .select('id')
    .single<{ id: string }>();
  if (error !== null || data === null) {
    throw new Error(`failed to seed api key: ${error?.message ?? 'no row returned'}`);
  }
  return data.id;
}

/** Ids of the rows a seeded plan tree creates, for read-tool assertions. */
export interface SeededPlanTree {
  planId: string;
  dayId: string;
  planExerciseId: string;
  setLogId: string;
  exerciseId: string;
}

/** Inserts a row through the given client and returns its id. */
async function insertRow(
  client: SupabaseClient,
  table: string,
  values: Record<string, unknown>,
): Promise<string> {
  const { data, error } = await client
    .from(table)
    .insert(values)
    .select('id')
    .single<{ id: string }>();
  if (error !== null || data === null) {
    throw new Error(`failed to seed ${table}: ${error?.message ?? 'no row returned'}`);
  }
  return data.id;
}

/**
 * Seeds a full plan tree (plan -> day -> exercise -> set log) for `userId`
 * through that user's own user-scoped client, so the inserts pass RLS exactly as
 * a real write would. The user-scoped client also reads the public catalog to
 * pick an arbitrary `exercise_id`; the service-role client cannot (and must not)
 * touch these user-owned tables. Returns every created id.
 */
export async function seedPlanTree(
  userClient: SupabaseClient,
  userId: string,
  planName: string,
): Promise<SeededPlanTree> {
  const exercise = await userClient
    .from('exercises')
    .select('id')
    .not('name', 'ilike', `${CATALOG_FIXTURE_PREFIX}%`)
    .limit(1)
    .single<{ id: string }>();
  if (exercise.error !== null || exercise.data === null) {
    throw new Error(`no catalog exercise to seed against: ${exercise.error?.message ?? 'empty'}`);
  }
  const exerciseId = exercise.data.id;
  const planId = await insertRow(userClient, 'plans', {
    user_id: userId,
    name: planName,
    kind: 'recomp',
    source: 'app',
  });
  const dayId = await insertRow(userClient, 'plan_days', {
    user_id: userId,
    plan_id: planId,
    position: 0,
    title: 'Day A',
    tag: 'push',
    source: 'app',
  });
  const planExerciseId = await insertRow(userClient, 'plan_exercises', {
    user_id: userId,
    day_id: dayId,
    exercise_id: exerciseId,
    position: 0,
    sets: 3,
    rep_min: 8,
    rep_max: 8,
    source: 'app',
  });
  const setLogId = await insertRow(userClient, 'set_logs', {
    user_id: userId,
    plan_exercise_id: planExerciseId,
    date: '2026-06-01',
    weight: 60,
    reps: [8, 8, 8],
    rir: 2,
    source: 'app',
  });
  return { planId, dayId, planExerciseId, setLogId, exerciseId };
}

/** Removes a test user (cascades to its `api_keys` and owned rows). */
export async function deleteUser(admin: SupabaseClient, userId: string): Promise<void> {
  await admin.auth.admin.deleteUser(userId);
}
