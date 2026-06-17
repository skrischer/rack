/**
 * Builds the rack-MCP `McpServer` and its tool surface.
 *
 * The server is built per request and receives the resolved {@link AuthContext}
 * so every tool operates as the authenticated user via the user-scoped Supabase
 * client (never service-role). The plan/day/exercise/set tools land in later
 * Phase 2 issues (see docs/specs/spec-rack-mcp.md); for now a single trivial
 * `ping` tool gives a connected client a non-empty tool list to verify the
 * transport end-to-end.
 */

import { McpServer } from '@modelcontextprotocol/sdk/server/mcp.js';

import type { AuthContext } from './auth.js';

/** Identifies this server to connecting MCP clients. */
const SERVER_INFO = { name: 'rack-mcp', version: '0.0.0' } as const;

/**
 * Creates a fresh `McpServer` for the resolved user with the current tool
 * surface registered. A new instance is built per request so the server stays
 * stateless and each request is scoped to exactly one resolved identity.
 */
export function buildServer(_auth: AuthContext): McpServer {
  const server = new McpServer(SERVER_INFO);

  server.registerTool(
    'ping',
    {
      title: 'Ping',
      description: 'Health check that confirms the rack-MCP server is reachable.',
    },
    () => ({ content: [{ type: 'text', text: 'pong' }] }),
  );

  return server;
}
