/**
 * Mints act-as-user Supabase tokens and verifies inbound Supabase user JWTs for
 * the rack-MCP. The two paths use deliberately different algorithms.
 *
 * Minting (act-as-user): an HS256 JWT signed with `SUPABASE_JWT_SECRET` carrying
 * the claims Supabase expects for an authenticated user (`sub`, `role`, `aud`),
 * so a client built with it is treated as that user and RLS applies. The minted
 * token lives only for the duration of one request burst and is never persisted.
 *
 * Verifying (admin mint endpoint): the caller authenticates with a genuine
 * Supabase user JWT — which the local and managed stacks sign asymmetrically with
 * ES256 — so it is verified against the project's JWKS, not the shared HS256
 * secret. The JWKS URL is derived from `SUPABASE_URL` so the same code works
 * locally and against managed Supabase. The token's `sub` is the only identity
 * trusted; no `user_id` is accepted from the request body. See
 * docs/specs/spec-rack-mcp.md.
 */

import { createRemoteJWKSet, jwtVerify, SignJWT, type JWTVerifyGetKey } from 'jose';

/** Lifetime of a minted user token; long enough for one request, short by design. */
const TOKEN_TTL_SECONDS = 60;

/** Audience Supabase stamps on (and the MCP requires of) authenticated user JWTs. */
const SUPABASE_AUDIENCE = 'authenticated';

/**
 * Mints a user-scoped Supabase access token for the given user id. The claims
 * match what Supabase verifies: `sub` = user id, `role`/`aud` = `authenticated`,
 * and a short `exp`. Signed with the shared HS256 secret, which PostgREST accepts
 * for RLS — distinct from how inbound user JWTs are verified.
 */
export async function mintUserToken(userId: string, jwtSecret: string): Promise<string> {
  const secret = new TextEncoder().encode(jwtSecret);
  return new SignJWT({ role: SUPABASE_AUDIENCE })
    .setProtectedHeader({ alg: 'HS256', typ: 'JWT' })
    .setSubject(userId)
    .setAudience(SUPABASE_AUDIENCE)
    .setIssuedAt()
    .setExpirationTime(`${TOKEN_TTL_SECONDS}s`)
    .sign(secret);
}

/** Remote JWKS resolver for the project's Auth; caches keys, so build once. */
export type SupabaseJwks = ReturnType<typeof createRemoteJWKSet>;

/**
 * Builds the remote JWKS resolver for the project's Auth, derived from
 * `supabaseUrl` so it works against both the local stack and managed Supabase.
 * The resolver caches fetched keys internally, so it should be created once at
 * startup and reused across requests rather than rebuilt per call.
 */
export function createSupabaseJwks(supabaseUrl: string): SupabaseJwks {
  return createRemoteJWKSet(new URL('/auth/v1/.well-known/jwks.json', supabaseUrl));
}

/**
 * Verifies an inbound Supabase user JWT against the project's JWKS (asymmetric
 * ES256) and returns its `sub` (the user id). Returns `null` when the token is
 * missing, malformed, expired, signed by a key not in the JWKS, or carries no
 * usable `sub` — the caller rejects all of these uniformly with 401. The `sub` is
 * the only identity the caller may trust; no `user_id` is read from anywhere else.
 * The `jwks` parameter is any jose key resolver: production passes the project
 * {@link SupabaseJwks}; a local resolver is accepted too, so the `exp`/signature
 * branches stay testable against a key set under the test's control.
 */
export async function verifyUserJwt(token: string, jwks: JWTVerifyGetKey): Promise<string | null> {
  try {
    const { payload } = await jwtVerify(token, jwks, { audience: SUPABASE_AUDIENCE });
    return typeof payload.sub === 'string' && payload.sub.length > 0 ? payload.sub : null;
  } catch {
    return null;
  }
}
