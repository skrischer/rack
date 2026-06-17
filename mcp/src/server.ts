/**
 * Builds the rack-MCP `McpServer` and its tool surface.
 *
 * This phase establishes the server and transport only; the per-user auth,
 * act-as-user Supabase client, and the plan/day/exercise/set tools land in
 * later Phase 2 issues (see docs/specs/spec-rack-mcp.md). For now a single
 * trivial `ping` tool gives a connected client a non-empty tool list to
 * verify the transport end-to-end.
 */

import { McpServer } from '@modelcontextprotocol/sdk/server/mcp.js';

/** Identifies this server to connecting MCP clients. */
const SERVER_INFO = { name: 'rack-mcp', version: '0.0.0' } as const;

/**
 * Creates a fresh `McpServer` with the current tool surface registered.
 * A new instance is built per request so the server stays stateless.
 */
export function buildServer(): McpServer {
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
