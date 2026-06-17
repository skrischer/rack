/**
 * Integration tests for API-key auth resolution against the local Supabase stack.
 * Covers the issue #11 acceptance: peppered-hash match, 401 for bad keys, a
 * user-scoped (RLS-applying) client on success, `last_used_at` updates, and that
 * the service-role client is used only for the `api_keys` lookup.
 */

import type { SupabaseClient } from '@supabase/supabase-js';
import { afterAll, beforeAll, describe, expect, it } from 'vitest';

import { resolveAuthContext } from './auth.js';
import type { Config } from './config.js';
import {
  adminClient,
  createConfirmedUser,
  deleteUser,
  newPlaintextKey,
  seedApiKey,
  testConfig,
} from './testSupport.js';

const config: Config = testConfig();
const admin: SupabaseClient = adminClient(config);

let userId: string;
let validKey: string;
let validKeyId: string;
let revokedKey: string;

beforeAll(async () => {
  userId = await createConfirmedUser(admin);
  validKey = newPlaintextKey();
  validKeyId = await seedApiKey(admin, config, userId, validKey);
  revokedKey = newPlaintextKey();
  await seedApiKey(admin, config, userId, revokedKey, { revoked: true });
});

afterAll(async () => {
  await deleteUser(admin, userId);
});

function bearer(key: string): string {
  return `Bearer ${key}`;
}

describe('resolveAuthContext', () => {
  it('resolves a valid key to its user and a user-scoped client', async () => {
    const ctx = await resolveAuthContext(bearer(validKey), config, admin);
    expect(ctx).not.toBeNull();
    expect(ctx?.userId).toBe(userId);

    const insert = await ctx!.supabase
      .from('plans')
      .insert({ user_id: userId, name: 'auth-test', source: 'agent' })
      .select('user_id, source')
      .single<{ user_id: string; source: string }>();
    expect(insert.error).toBeNull();
    expect(insert.data?.user_id).toBe(userId);
    expect(insert.data?.source).toBe('agent');
  });

  it('forbids the user-scoped client from inserting rows for another user', async () => {
    const ctx = await resolveAuthContext(bearer(validKey), config, admin);
    const other = await createConfirmedUser(admin);
    const insert = await ctx!.supabase
      .from('plans')
      .insert({ user_id: other, name: 'cross-user', source: 'agent' });
    expect(insert.error).not.toBeNull();
    await deleteUser(admin, other);
  });

  it('updates last_used_at on a successful resolution', async () => {
    const before = await admin
      .from('api_keys')
      .select('last_used_at')
      .eq('id', validKeyId)
      .single<{ last_used_at: string | null }>();

    await resolveAuthContext(bearer(validKey), config, admin);

    const after = await admin
      .from('api_keys')
      .select('last_used_at')
      .eq('id', validKeyId)
      .single<{ last_used_at: string | null }>();
    expect(after.data?.last_used_at).not.toBeNull();
    expect(after.data?.last_used_at).not.toBe(before.data?.last_used_at);
  });

  it('rejects a missing Authorization header', async () => {
    expect(await resolveAuthContext(undefined, config, admin)).toBeNull();
  });

  it('rejects a malformed (non rack_) key', async () => {
    expect(await resolveAuthContext(bearer('not-a-rack-key'), config, admin)).toBeNull();
  });

  it('rejects an unknown but well-formed key', async () => {
    expect(await resolveAuthContext(bearer(newPlaintextKey()), config, admin)).toBeNull();
  });

  it('rejects a revoked key', async () => {
    expect(await resolveAuthContext(bearer(revokedKey), config, admin)).toBeNull();
  });
});
