import type { AddressInfo } from 'node:net';

import { Client } from '@modelcontextprotocol/sdk/client/index.js';
import { StreamableHTTPClientTransport } from '@modelcontextprotocol/sdk/client/streamableHttp.js';
import type { SupabaseClient } from '@supabase/supabase-js';
import { afterAll, beforeAll, describe, expect, it } from 'vitest';

import type { Config } from './config.js';
import { createHttpServer } from './http.js';
import {
  adminClient,
  createConfirmedUser,
  deleteUser,
  newPlaintextKey,
  seedApiKey,
  testConfig,
} from './testSupport.js';

const config: Config = testConfig();
const admin: SupabaseClient = adminClient(config);
const httpServer = createHttpServer(config);

let baseUrl: URL;
let userId: string;
let validKey: string;

beforeAll(async () => {
  userId = await createConfirmedUser(admin);
  validKey = newPlaintextKey();
  await seedApiKey(admin, config, userId, validKey);
  await new Promise<void>((resolve) => httpServer.listen(0, resolve));
  const { port } = httpServer.address() as AddressInfo;
  baseUrl = new URL(`http://127.0.0.1:${port}/mcp`);
});

afterAll(async () => {
  await deleteUser(admin, userId);
  await new Promise<void>((resolve, reject) =>
    httpServer.close((err) => (err ? reject(err) : resolve())),
  );
});

function authedTransport(key: string): StreamableHTTPClientTransport {
  return new StreamableHTTPClientTransport(baseUrl, {
    requestInit: { headers: { Authorization: `Bearer ${key}` } },
  });
}

describe('Streamable HTTP MCP server', () => {
  it('lets a client with a valid key list the available tools', async () => {
    const client = new Client({ name: 'rack-mcp-test', version: '0.0.0' });
    await client.connect(authedTransport(validKey));

    const { tools } = await client.listTools();
    expect(tools.map((tool) => tool.name)).toContain('ping');

    await client.close();
  });

  it('rejects a connection with no API key before any tool runs', async () => {
    const client = new Client({ name: 'rack-mcp-test', version: '0.0.0' });
    const transport = new StreamableHTTPClientTransport(baseUrl);
    await expect(client.connect(transport)).rejects.toThrow();
  });

  it('rejects a connection with an unknown key', async () => {
    const client = new Client({ name: 'rack-mcp-test', version: '0.0.0' });
    await expect(client.connect(authedTransport(newPlaintextKey()))).rejects.toThrow();
  });
});
