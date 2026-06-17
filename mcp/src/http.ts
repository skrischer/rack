/**
 * Node HTTP wiring for the rack-MCP Streamable HTTP transport.
 *
 * Two routes are served. `/mcp` is the stateless Streamable HTTP transport:
 * every request first resolves its `Authorization` bearer API key to a
 * user-scoped {@link AuthContext} (rejecting with 401 before any tool runs),
 * then builds a fresh `McpServer` and `StreamableHTTPServerTransport` with no
 * session id, disposing both when the response closes. `/admin/keys` is the
 * JWT-authenticated key-mint endpoint (see {@link handleMintApiKey}). Per-request
 * auth keeps the process horizontally simple and scopes each request to one
 * identity.
 */

import { createServer, type IncomingMessage, type Server, type ServerResponse } from 'node:http';

import { StreamableHTTPServerTransport } from '@modelcontextprotocol/sdk/server/streamableHttp.js';
import type { SupabaseClient } from '@supabase/supabase-js';

import { ADMIN_KEYS_PATH, handleMintApiKey } from './adminRoutes.js';
import { resolveAuthContext } from './auth.js';
import type { Config } from './config.js';
import { buildServer } from './server.js';
import { createAdminClient } from './supabase.js';
import { createSupabaseJwks, type SupabaseJwks } from './userToken.js';

/** Path the Streamable HTTP transport is served from. */
const MCP_PATH = '/mcp';

/** Dependencies a request handler needs, fixed for the server's lifetime. */
interface RequestDeps {
  config: Config;
  adminClient: SupabaseClient;
  jwks: SupabaseJwks;
}

async function handleMcpRequest(
  req: IncomingMessage,
  res: ServerResponse,
  deps: RequestDeps,
): Promise<void> {
  const auth = await resolveAuthContext(req.headers.authorization, deps.config, deps.adminClient);
  if (auth === null) {
    res.writeHead(401, { 'content-type': 'application/json' });
    res.end(JSON.stringify({ error: 'unauthorized' }));
    return;
  }

  const server = buildServer(auth);
  const transport = new StreamableHTTPServerTransport({ sessionIdGenerator: undefined });

  res.on('close', () => {
    void transport.close();
    void server.close();
  });

  await server.connect(transport);
  await transport.handleRequest(req, res);
}

function dispatch(req: IncomingMessage, res: ServerResponse, deps: RequestDeps): Promise<void> {
  const url = new URL(req.url ?? '/', 'http://localhost');
  if (url.pathname === MCP_PATH) {
    return handleMcpRequest(req, res, deps);
  }
  if (url.pathname === ADMIN_KEYS_PATH) {
    return handleMintApiKey(req, res, deps.config, deps.jwks);
  }
  res.writeHead(404).end();
  return Promise.resolve();
}

function route(req: IncomingMessage, res: ServerResponse, deps: RequestDeps): void {
  dispatch(req, res, deps).catch(() => {
    if (!res.headersSent) {
      res.writeHead(500).end();
    }
  });
}

/**
 * Creates the Node HTTP server that serves the MCP transport at `/mcp` and the
 * key-mint endpoint at `/admin/keys`. The service-role admin client (used only
 * for the pre-auth `api_keys` resolution lookup, never for any write) and the
 * JWKS resolver (for verifying inbound user JWTs) are both built once for the
 * server's lifetime. The mint write runs as the resolved user under RLS, so it
 * does not use the admin client.
 */
export function createHttpServer(config: Config): Server {
  const deps: RequestDeps = {
    config,
    adminClient: createAdminClient(config),
    jwks: createSupabaseJwks(config.supabaseUrl),
  };
  return createServer((req, res) => route(req, res, deps));
}
