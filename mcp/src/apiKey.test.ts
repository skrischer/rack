import { describe, expect, it } from 'vitest';

import { API_KEY_PREFIX, hashApiKey, hashesEqual, isWellFormedApiKey, keyPrefix } from './apiKey.js';

const PEPPER = 'test-pepper';

describe('hashApiKey', () => {
  it('produces a stable lowercase hex SHA-256 digest', () => {
    const hash = hashApiKey('rack_secret', PEPPER);
    expect(hash).toMatch(/^[0-9a-f]{64}$/);
    expect(hashApiKey('rack_secret', PEPPER)).toBe(hash);
  });

  it('depends on the pepper', () => {
    expect(hashApiKey('rack_secret', PEPPER)).not.toBe(hashApiKey('rack_secret', 'other'));
  });

  it('depends on the key', () => {
    expect(hashApiKey('rack_a', PEPPER)).not.toBe(hashApiKey('rack_b', PEPPER));
  });
});

describe('hashesEqual', () => {
  it('is true for identical hashes', () => {
    const hash = hashApiKey('rack_secret', PEPPER);
    expect(hashesEqual(hash, hash)).toBe(true);
  });

  it('is false for different hashes', () => {
    expect(hashesEqual(hashApiKey('rack_a', PEPPER), hashApiKey('rack_b', PEPPER))).toBe(false);
  });

  it('is false (not throwing) for length mismatch', () => {
    expect(hashesEqual('ab', hashApiKey('rack_secret', PEPPER))).toBe(false);
  });
});

describe('isWellFormedApiKey', () => {
  it('accepts a prefixed key with a body', () => {
    expect(isWellFormedApiKey(`${API_KEY_PREFIX}abc`)).toBe(true);
  });

  it('rejects the bare prefix and unprefixed values', () => {
    expect(isWellFormedApiKey(API_KEY_PREFIX)).toBe(false);
    expect(isWellFormedApiKey('abc')).toBe(false);
    expect(isWellFormedApiKey('')).toBe(false);
  });
});

describe('keyPrefix', () => {
  it('keeps the rack_ prefix plus a few body characters', () => {
    expect(keyPrefix(`${API_KEY_PREFIX}abcdef0123456789`)).toBe(`${API_KEY_PREFIX}abcdef`);
  });

  it('exposes far less than the full secret', () => {
    const key = `${API_KEY_PREFIX}abcdef0123456789`;
    const prefix = keyPrefix(key);
    expect(key.startsWith(prefix)).toBe(true);
    expect(prefix.length).toBeLessThan(key.length);
  });
});
