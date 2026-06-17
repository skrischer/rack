/**
 * Node HTTP wiring for the rack-MCP Streamable HTTP transport.
 *
 * The server is stateless: each request builds a fresh `McpServer` and a fresh
 * `StreamableHTTPServerTransport` with no session id, then disposes both when
 * the response closes. This keeps per-request auth resolution (a later Phase 2
 * issue) self-contained and the process horizontally simple.
 */

import { createServer, type IncomingMessage, type Server, type ServerResponse } from 'node:http';

import { StreamableHTTPServerTransport } from '@modelcontextprotocol/sdk/server/streamableHttp.js';

import { buildServer } from './server.js';

/** Path the Streamable HTTP transport is served from. */
const MCP_PATH = '/mcp';

async function handleMcpRequest(req: IncomingMessage, res: ServerResponse): Promise<void> {
  const server = buildServer();
  const transport = new StreamableHTTPServerTransport({ sessionIdGenerator: undefined });

  res.on('close', () => {
    void transport.close();
    void server.close();
  });

  await server.connect(transport);
  await transport.handleRequest(req, res);
}

function route(req: IncomingMessage, res: ServerResponse): void {
  const url = new URL(req.url ?? '/', 'http://localhost');
  if (url.pathname !== MCP_PATH) {
    res.writeHead(404).end();
    return;
  }
  handleMcpRequest(req, res).catch(() => {
    if (!res.headersSent) {
      res.writeHead(500).end();
    }
  });
}

/** Creates the Node HTTP server that serves the MCP transport at `/mcp`. */
export function createHttpServer(): Server {
  return createServer(route);
}
