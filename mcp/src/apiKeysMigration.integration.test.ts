/**
 * Migration check for issue #31 against the local Supabase stack: the
 * `key_prefix` and `revoked_at` columns added by
 * `20260617150000_alter_api_keys_add_prefix_revoked_at.sql` exist and accept
 * values, and the Phase 1 owner RLS policy on `api_keys` (`user_id =
 * auth.uid()`) is unchanged — a user can read its own row but never another
 * user's. All reads/writes run as the resolved user via a minted user token
 * (RLS applies); the admin client is used for fixture setup only. See
 * docs/specs/spec-api-key-management.md.
 */

import type { SupabaseClient } from '@supabase/supabase-js';
import { afterAll, beforeAll, describe, expect, it } from 'vitest';

import { hashApiKey } from './apiKey.js';
import type { Config } from './config.js';
import { createUserClient } from './supabase.js';
import { mintUserToken } from './userToken.js';
import {
  adminClient,
  createConfirmedUser,
  deleteUser,
  randomToken,
  testConfig,
} from './testSupport.js';

const config: Config = testConfig();
const admin: SupabaseClient = adminClient(config);

let userA: string;
let userB: string;
let clientA: SupabaseClient;

/** A unique peppered hash so repeated/parallel runs never collide on a row. */
function uniqueHash(): string {
  return hashApiKey(`mig-${randomToken()}`, config.apiKeyPepper);
}

beforeAll(async () => {
  userA = await createConfirmedUser(admin);
  userB = await createConfirmedUser(admin);
  clientA = createUserClient(config, await mintUserToken(userA, config.supabaseJwtSecret));
});

afterAll(async () => {
  await deleteUser(admin, userA);
  await deleteUser(admin, userB);
});

describe('api_keys key_prefix + revoked_at migration', () => {
  it('persists key_prefix and revoked_at written as the resolved user', async () => {
    const revokedAt = new Date().toISOString();
    const inserted = await clientA
      .from('api_keys')
      .insert({
        user_id: userA,
        key_hash: uniqueHash(),
        name: 'migration-check',
        key_prefix: 'rack_abcd',
        revoked: true,
        revoked_at: revokedAt,
      })
      .select('id, key_prefix, revoked, revoked_at')
      .single<{ id: string; key_prefix: string; revoked: boolean; revoked_at: string }>();

    expect(inserted.error).toBeNull();
    expect(inserted.data?.key_prefix).toBe('rack_abcd');
    expect(inserted.data?.revoked).toBe(true);
    expect(inserted.data?.revoked_at).not.toBeNull();
  });

  it('keeps key_prefix and revoked_at nullable for non-revoked keys', async () => {
    const inserted = await clientA
      .from('api_keys')
      .insert({ user_id: userA, key_hash: uniqueHash(), name: 'no-prefix' })
      .select('key_prefix, revoked_at')
      .single<{ key_prefix: string | null; revoked_at: string | null }>();

    expect(inserted.error).toBeNull();
    expect(inserted.data?.key_prefix).toBeNull();
    expect(inserted.data?.revoked_at).toBeNull();
  });

  it('still enforces the owner RLS policy: a user cannot read another user rows', async () => {
    await clientA
      .from('api_keys')
      .insert({ user_id: userA, key_hash: uniqueHash(), key_prefix: 'rack_rls0' });

    const clientB = createUserClient(config, await mintUserToken(userB, config.supabaseJwtSecret));
    const seenByB = await clientB.from('api_keys').select('id').eq('user_id', userA);
    expect(seenByB.error).toBeNull();
    expect(seenByB.data).toEqual([]);

    const seenByA = await clientA.from('api_keys').select('id').eq('user_id', userA);
    expect(seenByA.error).toBeNull();
    expect((seenByA.data ?? []).length).toBeGreaterThan(0);
  });
});
