import { describe, expect, it } from 'vitest';

import { revokeApiKeyParamsSchema } from './adminKeys.js';

describe('revokeApiKeyParamsSchema', () => {
  it('accepts a uuid key id', () => {
    const parsed = revokeApiKeyParamsSchema.safeParse({
      id: '00000000-0000-0000-0000-000000000000',
    });
    expect(parsed.success).toBe(true);
  });

  it('rejects a non-uuid key id', () => {
    expect(revokeApiKeyParamsSchema.safeParse({ id: 'not-a-uuid' }).success).toBe(false);
  });

  it('rejects an empty id', () => {
    expect(revokeApiKeyParamsSchema.safeParse({ id: '' }).success).toBe(false);
  });

  it('rejects extra fields such as a user_id selector', () => {
    expect(
      revokeApiKeyParamsSchema.safeParse({
        id: '00000000-0000-0000-0000-000000000000',
        user_id: 'someone-else',
      }).success,
    ).toBe(false);
  });
});
