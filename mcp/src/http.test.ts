import type { AddressInfo } from 'node:net';

import { Client } from '@modelcontextprotocol/sdk/client/index.js';
import { StreamableHTTPClientTransport } from '@modelcontextprotocol/sdk/client/streamableHttp.js';
import { afterAll, beforeAll, describe, expect, it } from 'vitest';

import { createHttpServer } from './http.js';

let baseUrl: URL;
const httpServer = createHttpServer();

beforeAll(async () => {
  await new Promise<void>((resolve) => httpServer.listen(0, resolve));
  const { port } = httpServer.address() as AddressInfo;
  baseUrl = new URL(`http://127.0.0.1:${port}/mcp`);
});

afterAll(async () => {
  await new Promise<void>((resolve, reject) =>
    httpServer.close((err) => (err ? reject(err) : resolve())),
  );
});

describe('Streamable HTTP MCP server', () => {
  it('lets a connected client list the available tools', async () => {
    const client = new Client({ name: 'rack-mcp-test', version: '0.0.0' });
    const transport = new StreamableHTTPClientTransport(baseUrl);
    await client.connect(transport);

    const { tools } = await client.listTools();
    expect(tools.map((tool) => tool.name)).toContain('ping');

    await client.close();
  });
});
