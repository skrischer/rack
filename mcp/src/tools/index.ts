/**
 * Registers the rack-MCP tool surface on a per-request server.
 *
 * Every tool receives the resolved {@link AuthContext} and queries or mutates
 * through its user-scoped Supabase client, so RLS limits access to the caller's
 * rows (the exercise catalog is the one public, read-only table). No tool
 * exposes a `user_id` or account selector; writes inject the caller's id and
 * `source='agent'` from the auth context. See docs/specs/spec-rack-mcp.md.
 */

import { McpServer } from '@modelcontextprotocol/sdk/server/mcp.js';

import type { AuthContext } from '../auth.js';
import { registerExerciseTools } from './exercises.js';
import { registerPlanTools } from './plans.js';
import { registerSetLogTools } from './setLogs.js';
import { registerWriteTools } from './writeTools.js';

/** Registers all read tools (plans, plan tree, set logs, catalog search). */
export function registerReadTools(server: McpServer, auth: AuthContext): void {
  registerPlanTools(server, auth);
  registerSetLogTools(server, auth);
  registerExerciseTools(server, auth);
}

/** Registers all tools (read + write) for the resolved user. */
export function registerTools(server: McpServer, auth: AuthContext): void {
  registerReadTools(server, auth);
  registerWriteTools(server, auth);
}
