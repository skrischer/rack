import { describe, expect, it } from 'vitest';

import { extractBearerKey } from './auth.js';

describe('extractBearerKey', () => {
  it('extracts the token from a Bearer header', () => {
    expect(extractBearerKey('Bearer rack_abc')).toBe('rack_abc');
  });

  it('is case-insensitive on the scheme', () => {
    expect(extractBearerKey('bearer rack_abc')).toBe('rack_abc');
  });

  it('returns null for a missing header', () => {
    expect(extractBearerKey(undefined)).toBeNull();
  });

  it('returns null for a non-bearer scheme', () => {
    expect(extractBearerKey('Basic rack_abc')).toBeNull();
  });

  it('returns null for a bearer header with no token', () => {
    expect(extractBearerKey('Bearer ')).toBeNull();
    expect(extractBearerKey('Bearer')).toBeNull();
  });
});
