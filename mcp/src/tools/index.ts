/**
 * Registers the rack-MCP read tool surface on a per-request server.
 *
 * Every read tool receives the resolved {@link AuthContext} and queries through
 * its user-scoped Supabase client, so RLS limits results to the caller's rows
 * (the exercise catalog is the one public table). No tool exposes a `user_id`
 * or account selector. Write tools land in a later Phase 2 issue. See
 * docs/specs/spec-rack-mcp.md.
 */

import { McpServer } from '@modelcontextprotocol/sdk/server/mcp.js';

import type { AuthContext } from '../auth.js';
import { registerExerciseTools } from './exercises.js';
import { registerPlanTools } from './plans.js';
import { registerSetLogTools } from './setLogs.js';

/** Registers all read tools (plans, plan tree, set logs, catalog search). */
export function registerReadTools(server: McpServer, auth: AuthContext): void {
  registerPlanTools(server, auth);
  registerSetLogTools(server, auth);
  registerExerciseTools(server, auth);
}
