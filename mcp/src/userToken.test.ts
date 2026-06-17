import { jwtVerify } from 'jose';
import { describe, expect, it } from 'vitest';

import { createSupabaseJwks, mintUserToken, verifyUserJwt } from './userToken.js';

const SECRET = 'test-jwt-secret-at-least-32-characters-long';

describe('mintUserToken', () => {
  it('mints an HS256 token verifiable with the shared secret', async () => {
    const token = await mintUserToken('user-abc', SECRET);
    const { payload, protectedHeader } = await jwtVerify(
      token,
      new TextEncoder().encode(SECRET),
      { audience: 'authenticated' },
    );
    expect(protectedHeader.alg).toBe('HS256');
    expect(payload.sub).toBe('user-abc');
    expect(payload.role).toBe('authenticated');
  });
});

describe('verifyUserJwt', () => {
  // The inbound-JWT happy path (a real ES256 user token fetched from the local
  // Auth's JWKS) is exercised end-to-end in mint.integration.test.ts. Here we
  // assert the network-free rejection path: a malformed token never reaches the
  // JWKS resolver and resolves to null so the caller can answer 401 uniformly.
  it('returns null for a malformed token', async () => {
    const jwks = createSupabaseJwks('http://127.0.0.1:55321');
    expect(await verifyUserJwt('not-a-jwt', jwks)).toBeNull();
  });
});
