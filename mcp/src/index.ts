/**
 * rack-MCP entry point.
 *
 * Loads and validates configuration from the environment, then starts the
 * Streamable HTTP MCP server on the configured port. Auth resolution, the
 * act-as-user Supabase client, and the data tools land in later Phase 2 issues
 * (see docs/specs/spec-rack-mcp.md).
 */

import { loadConfig } from './config.js';
import { createHttpServer } from './http.js';

function main(): void {
  const config = loadConfig();
  const httpServer = createHttpServer();
  httpServer.listen(config.mcpPort, () => {
    console.log(`rack-mcp listening on http://localhost:${config.mcpPort}/mcp`);
  });
}

main();
