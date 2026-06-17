/**
 * Read tool over the global exercise catalog. The catalog (`exercises`) is the
 * one public, non-user-owned table: its RLS grants every authenticated caller
 * SELECT, so this search runs through the same user-scoped client and returns
 * public catalog rows (never user data). See docs/specs/spec-rack-mcp.md.
 */

import { McpServer } from '@modelcontextprotocol/sdk/server/mcp.js';
import { z } from 'zod';

import type { AuthContext } from '../auth.js';
import { jsonResult } from './result.js';

/** Columns returned for a catalog row, including CC-BY-SA attribution. */
const EXERCISE_COLUMNS = 'id, name, category, muscles, equipment, license, license_author';

/** Default maximum rows returned when no `limit` is given. */
const DEFAULT_LIMIT = 25;

/** Zod input shape: a required search term plus optional category and limit. */
const inputSchema = {
  q: z.string({ message: 'q must be a string' }).min(1, { message: 'q must not be empty' }),
  category: z.string().min(1).optional(),
  limit: z.number().int().positive().max(100).optional(),
} as const;

/** Parsed and validated input for `search_exercises`. */
type ExerciseQuery = { q: string; category?: string; limit?: number };

/** Escapes PostgREST `ilike` wildcards so the term matches literally. */
function escapeLike(term: string): string {
  return term.replace(/[\\%_]/g, (match) => `\\${match}`);
}

/** Runs the catalog search by name, with an optional category filter. */
async function searchCatalog(auth: AuthContext, input: ExerciseQuery): Promise<unknown> {
  let query = auth.supabase
    .from('exercises')
    .select(EXERCISE_COLUMNS)
    .ilike('name', `%${escapeLike(input.q)}%`);
  if (input.category !== undefined) {
    query = query.eq('category', input.category);
  }
  const { data, error } = await query
    .order('name', { ascending: true })
    .limit(input.limit ?? DEFAULT_LIMIT);
  if (error !== null) {
    throw new Error(`failed to search exercises: ${error.message}`);
  }
  return data;
}

/** Registers `search_exercises` on the server. */
export function registerExerciseTools(server: McpServer, auth: AuthContext): void {
  server.registerTool(
    'search_exercises',
    {
      title: 'Search exercises',
      description: 'Search the global exercise catalog by name (case-insensitive).',
      inputSchema,
    },
    async (input) => jsonResult(await searchCatalog(auth, input)),
  );
}
