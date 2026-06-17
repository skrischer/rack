/**
 * Migration check for issue #39 against the local Supabase stack: the private
 * `artifacts` Storage bucket created by
 * `20260617160000_create_artifacts_storage_bucket.sql` exists, and its per-user
 * `storage.objects` policies confine each user to objects whose first path
 * segment equals their own `auth.uid()`. A user can upload, list, and read an
 * object under `{user_id}/...` but never another user's prefix. All Storage
 * calls run as the resolved user via a minted user token (the policies apply);
 * the admin client is used for fixture user setup only, never for the uploads.
 * See docs/specs/spec-visualization-artifacts.md.
 */

import type { SupabaseClient } from '@supabase/supabase-js';
import { afterAll, beforeAll, describe, expect, it } from 'vitest';

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

const BUCKET = 'artifacts';

let userA: string;
let userB: string;
let clientA: SupabaseClient;
let clientB: SupabaseClient;

/** A small artifact body to round-trip through the private bucket. */
function htmlBody(): Blob {
  return new Blob(['<html><body>chart</body></html>'], { type: 'text/html' });
}

beforeAll(async () => {
  userA = await createConfirmedUser(admin);
  userB = await createConfirmedUser(admin);
  clientA = createUserClient(config, await mintUserToken(userA, config.supabaseJwtSecret));
  clientB = createUserClient(config, await mintUserToken(userB, config.supabaseJwtSecret));
});

afterAll(async () => {
  await deleteUser(admin, userA);
  await deleteUser(admin, userB);
});

describe('artifacts private Storage bucket migration', () => {
  it('exposes the bucket as private (not public)', async () => {
    const { data, error } = await admin.storage.getBucket(BUCKET);
    expect(error).toBeNull();
    expect(data?.public).toBe(false);
  });

  it('lets a user upload and read an object under their own uid prefix', async () => {
    const path = `${userA}/${randomToken()}.html`;
    const uploaded = await clientA.storage.from(BUCKET).upload(path, htmlBody());
    expect(uploaded.error).toBeNull();

    const downloaded = await clientA.storage.from(BUCKET).download(path);
    expect(downloaded.error).toBeNull();
    expect(await downloaded.data?.text()).toContain('chart');
  });

  it('rejects an upload to another user uid prefix', async () => {
    const path = `${userA}/${randomToken()}.html`;
    const uploaded = await clientB.storage.from(BUCKET).upload(path, htmlBody());
    expect(uploaded.error).not.toBeNull();
  });

  it('does not list or read another user object', async () => {
    const path = `${userA}/${randomToken()}.html`;
    const uploaded = await clientA.storage.from(BUCKET).upload(path, htmlBody());
    expect(uploaded.error).toBeNull();

    const listedByB = await clientB.storage.from(BUCKET).list(userA);
    expect(listedByB.error).toBeNull();
    expect(listedByB.data ?? []).toEqual([]);

    const downloadByB = await clientB.storage.from(BUCKET).download(path);
    expect(downloadByB.error).not.toBeNull();
  });
});
