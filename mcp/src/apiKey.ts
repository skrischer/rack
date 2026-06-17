/**
 * API-key hashing primitives for the rack-MCP.
 *
 * A per-user API key has the form `rack_<random>`. Only a peppered SHA-256 hash
 * of the full key is ever persisted in `api_keys.key_hash` (see
 * docs/specs/spec-rack-mcp.md). Keys are high-entropy random strings, so a fast
 * keyed hash with a server-side pepper is sufficient and avoids a bcrypt/argon2
 * dependency; the pepper hardens against offline attacks on a DB dump.
 */

import { createHash, timingSafeEqual } from 'node:crypto';

/** Prefix every minted key carries, used as a cheap malformed-input filter. */
export const API_KEY_PREFIX = 'rack_';

/**
 * Computes the peppered SHA-256 hash of an API key, returned as lowercase hex.
 * The pepper is prepended to the key so the digest cannot be reproduced from a
 * stolen DB row alone.
 */
export function hashApiKey(key: string, pepper: string): string {
  return createHash('sha256').update(pepper).update(key).digest('hex');
}

/**
 * Constant-time comparison of two hex-encoded hashes. Returns false (rather than
 * throwing) when the lengths differ, so callers can treat any mismatch uniformly
 * without leaking timing information about which check failed.
 */
export function hashesEqual(a: string, b: string): boolean {
  const bufferA = Buffer.from(a, 'hex');
  const bufferB = Buffer.from(b, 'hex');
  if (bufferA.length !== bufferB.length) {
    return false;
  }
  return timingSafeEqual(bufferA, bufferB);
}

/** True when a candidate string has the expected key shape (`rack_` + body). */
export function isWellFormedApiKey(key: string): boolean {
  return key.startsWith(API_KEY_PREFIX) && key.length > API_KEY_PREFIX.length;
}
