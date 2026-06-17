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

/** Removes a test user (cascades to its `api_keys` and owned rows). */
export async function deleteUser(admin: SupabaseClient, userId: string): Promise<void> {
  await admin.auth.admin.deleteUser(userId);
}
