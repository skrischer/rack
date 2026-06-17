/**
 * Mints short-lived user-scoped Supabase access tokens for the rack-MCP.
 *
 * The token is an HS256 JWT signed with `SUPABASE_JWT_SECRET` carrying the
 * claims Supabase expects for an authenticated user (`sub`, `role`, `aud`), so
 * a client built with it is treated as that user and RLS applies (see
 * docs/specs/spec-rack-mcp.md). The token lives only for the duration of one
 * request burst; it is never persisted.
 */

import { SignJWT } from 'jose';

/** Lifetime of a minted user token; long enough for one request, short by design. */
const TOKEN_TTL_SECONDS = 60;

/**
 * Mints a user-scoped Supabase access token for the given user id. The claims
 * match what Supabase verifies: `sub` = user id, `role`/`aud` = `authenticated`,
 * and a short `exp`.
 */
export async function mintUserToken(userId: string, jwtSecret: string): Promise<string> {
  const secret = new TextEncoder().encode(jwtSecret);
  return new SignJWT({ role: 'authenticated' })
    .setProtectedHeader({ alg: 'HS256', typ: 'JWT' })
    .setSubject(userId)
    .setAudience('authenticated')
    .setIssuedAt()
    .setExpirationTime(`${TOKEN_TTL_SECONDS}s`)
    .sign(secret);
}
