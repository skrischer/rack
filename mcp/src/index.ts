/**
 * rack-MCP entry point.
 *
 * Loads the gitignored `.env` (if present) and validates configuration, then
 * starts the Streamable HTTP MCP server on the configured port. Each request is
 * authenticated by its bearer API key and acts as the resolved user; the data
 * tools and admin mint endpoint land in later Phase 2 issues (see
 * docs/specs/spec-rack-mcp.md).
 */

import { existsSync } from 'node:fs';

import { loadConfig } from './config.js';
import { createHttpServer } from './http.js';

/** Loads `.env` into `process.env` when it exists; real envs may set vars directly. */
function loadEnvFile(): void {
  if (existsSync('.env')) {
    process.loadEnvFile('.env');
  }
}

function main(): void {
  loadEnvFile();
  const config = loadConfig();
  const httpServer = createHttpServer(config);
  httpServer.listen(config.mcpPort, () => {
    console.log(`rack-mcp listening on http://localhost:${config.mcpPort}/mcp`);
  });
}

main();
