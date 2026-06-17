import { describe, expect, it } from 'vitest';

import { mintApiKeyBodySchema } from './mintApiKey.js';

describe('mintApiKeyBodySchema', () => {
  it('accepts a named key', () => {
    const parsed = mintApiKeyBodySchema.safeParse({ name: 'laptop' });
    expect(parsed.success).toBe(true);
    expect(parsed.data?.name).toBe('laptop');
  });

  it('trims surrounding whitespace from the name', () => {
    const parsed = mintApiKeyBodySchema.safeParse({ name: '  laptop  ' });
    expect(parsed.success).toBe(true);
    expect(parsed.data?.name).toBe('laptop');
  });

  it('requires a name', () => {
    expect(mintApiKeyBodySchema.safeParse({}).success).toBe(false);
  });

  it('rejects a caller-supplied user_id', () => {
    expect(mintApiKeyBodySchema.safeParse({ user_id: 'someone-else' }).success).toBe(false);
  });

  it('rejects an empty name', () => {
    expect(mintApiKeyBodySchema.safeParse({ name: '' }).success).toBe(false);
  });

  it('rejects a whitespace-only name', () => {
    expect(mintApiKeyBodySchema.safeParse({ name: '   ' }).success).toBe(false);
  });

  it('accepts a 64-char name but rejects a 65-char name', () => {
    expect(mintApiKeyBodySchema.safeParse({ name: 'a'.repeat(64) }).success).toBe(true);
    expect(mintApiKeyBodySchema.safeParse({ name: 'a'.repeat(65) }).success).toBe(false);
  });

  it('rejects a non-string name', () => {
    expect(mintApiKeyBodySchema.safeParse({ name: 42 }).success).toBe(false);
  });
});
