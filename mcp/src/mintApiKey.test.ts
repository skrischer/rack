import { describe, expect, it } from 'vitest';

import { mintApiKeyBodySchema } from './mintApiKey.js';

describe('mintApiKeyBodySchema', () => {
  it('accepts an empty body', () => {
    expect(mintApiKeyBodySchema.safeParse({}).success).toBe(true);
  });

  it('accepts a named key', () => {
    const parsed = mintApiKeyBodySchema.safeParse({ name: 'laptop' });
    expect(parsed.success).toBe(true);
    expect(parsed.data?.name).toBe('laptop');
  });

  it('rejects a caller-supplied user_id', () => {
    expect(mintApiKeyBodySchema.safeParse({ user_id: 'someone-else' }).success).toBe(false);
  });

  it('rejects an empty name', () => {
    expect(mintApiKeyBodySchema.safeParse({ name: '' }).success).toBe(false);
  });

  it('rejects a non-string name', () => {
    expect(mintApiKeyBodySchema.safeParse({ name: 42 }).success).toBe(false);
  });
});
