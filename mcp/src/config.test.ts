import { describe, expect, it } from 'vitest';

import { loadConfig } from './config.js';

const validEnv = {
  SUPABASE_URL: 'http://127.0.0.1:55321',
  SUPABASE_ANON_KEY: 'anon',
  SUPABASE_SERVICE_ROLE_KEY: 'service',
  SUPABASE_JWT_SECRET: 'jwt-secret',
  API_KEY_PEPPER: 'pepper',
} satisfies NodeJS.ProcessEnv;

describe('loadConfig', () => {
  it('parses a complete environment and defaults the port', () => {
    const config = loadConfig(validEnv);
    expect(config.supabaseUrl).toBe('http://127.0.0.1:55321');
    expect(config.mcpPort).toBe(8787);
  });

  it('coerces MCP_PORT to a number', () => {
    const config = loadConfig({ ...validEnv, MCP_PORT: '9090' });
    expect(config.mcpPort).toBe(9090);
  });

  it('rejects a missing required value', () => {
    const withoutUrl: NodeJS.ProcessEnv = { ...validEnv };
    delete withoutUrl.SUPABASE_URL;
    expect(() => loadConfig(withoutUrl)).toThrow();
  });
});
