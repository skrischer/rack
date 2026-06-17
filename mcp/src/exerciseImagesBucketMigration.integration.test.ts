/**
 * Migration check for issue #47 against the local Supabase stack: the public
 * `exercise-images` Storage bucket created by
 * `20260617180000_create_exercise_images_storage_bucket.sql` exists, is public,
 * and is read-only over the Data API. The anon role can read an object's public
 * URL, while neither the anon nor an authenticated user can write to the bucket
 * (no insert policy is granted). The bucket holds only CC-BY-SA exercise images,
 * never user data. The admin client seeds an object (acting as the bucket owner,
 * mirroring the seed/enrichment routine) for the read assertions only.
 * See docs/specs/spec-exercise-detail.md.
 */

import { createClient, type SupabaseClient } from '@supabase/supabase-js';
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

const BUCKET = 'exercise-images';

let user: string;
let authedClient: SupabaseClient;
let anonClient: SupabaseClient;
let seededPath: string;

/** A tiny PNG-typed body to round-trip through the public bucket. */
function imageBody(): Blob {
  return new Blob(['fake-png-bytes'], { type: 'image/png' });
}

beforeAll(async () => {
  user = await createConfirmedUser(admin);
  authedClient = createUserClient(config, await mintUserToken(user, config.supabaseJwtSecret));
  anonClient = createClient(config.supabaseUrl, config.supabaseAnonKey, {
    auth: { autoRefreshToken: false, persistSession: false },
  });
  // The enrichment routine (owner-privileged) populates the bucket; the admin
  // client stands in for it here so the read assertions have an object.
  seededPath = `bench-press/${randomToken()}.png`;
  const uploaded = await admin.storage.from(BUCKET).upload(seededPath, imageBody());
  expect(uploaded.error).toBeNull();
});

afterAll(async () => {
  await admin.storage.from(BUCKET).remove([seededPath]);
  await deleteUser(admin, user);
});

describe('exercise-images public Storage bucket migration', () => {
  it('exposes the bucket as public', async () => {
    const { data, error } = await admin.storage.getBucket(BUCKET);
    expect(error).toBeNull();
    expect(data?.public).toBe(true);
  });

  it('lets the anon role read an object via its public URL', async () => {
    const { data } = anonClient.storage.from(BUCKET).getPublicUrl(seededPath);
    const response = await fetch(data.publicUrl);
    expect(response.ok).toBe(true);
    expect(await response.text()).toContain('fake-png-bytes');
  });

  it('rejects an upload from the anon role', async () => {
    const uploaded = await anonClient.storage
      .from(BUCKET)
      .upload(`bench-press/${randomToken()}.png`, imageBody());
    expect(uploaded.error).not.toBeNull();
  });

  it('rejects an upload from an authenticated user', async () => {
    const uploaded = await authedClient.storage
      .from(BUCKET)
      .upload(`bench-press/${randomToken()}.png`, imageBody());
    expect(uploaded.error).not.toBeNull();
  });
});
