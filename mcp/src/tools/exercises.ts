/**
 * Read tool over the global exercise catalog. The catalog (`exercises`) is the
 * one public, non-user-owned table: its RLS grants every authenticated caller
 * SELECT, so this search runs through the same user-scoped client and returns
 * public catalog rows (never user data). See docs/specs/spec-rack-mcp.md.
 *
 * Search is relevance-ranked, multi-word and alias-aware: each whitespace token
 * of `q` is matched against name + aliases via pg_trgm word-similarity
 * (token-AND — every token must match), rows rank by the summed per-token score
 * with `is_canonical` as the tiebreak, and the heavy lifting lives in the
 * `public.search_exercises` SQL function (see the search RPC migration). With `q`
 * omitted the tool is a filter-only browse. See docs/specs/spec-catalog-search.md.
 */

import { McpServer } from '@modelcontextprotocol/sdk/server/mcp.js';
import { z } from 'zod';

import type { AuthContext } from '../auth.js';
import { jsonResult } from './result.js';

/**
 * Columns returned for a catalog row, including the `aliases` / `is_canonical`
 * curation fields and the CC-BY-SA attribution. The ranking `score` the RPC also
 * returns is used only for ordering and is projected out of the output.
 */
const EXERCISE_COLUMNS =
  'id, name, category, muscles, equipment, license, license_author, aliases, is_canonical';

/** Default maximum rows returned when no `limit` is given. */
const DEFAULT_LIMIT = 25;

/**
 * Zod input shape: an optional search term plus optional equipment / muscle /
 * category filters and a row limit. With `q` omitted the tool browses by filters
 * alone (ordered canonical-first, then by name).
 */
const inputSchema = {
  q: z
    .string({ message: 'q must be a string' })
    .min(1, { message: 'q must not be empty' })
    .optional(),
  equipment: z.string().min(1).optional(),
  muscle: z.string().min(1).optional(),
  category: z.string().min(1).optional(),
  limit: z.number().int().positive().max(100).optional(),
} as const;

/** Parsed and validated input for `search_exercises`. */
type ExerciseQuery = {
  q?: string;
  equipment?: string;
  muscle?: string;
  category?: string;
  limit?: number;
};

/**
 * Runs the catalog search via the `search_exercises` RPC, which does the
 * token-AND word-similarity matching, filtering, ranking (summed similarity,
 * canonical-first, then a name-similarity tiebreak) and top-N limiting in one
 * SQL statement. The `.select()` here only projects the catalog columns (dropping
 * the RPC's internal `score`/`tiebreak`); PostgREST preserves the function's
 * own row order (a column-only projection does not re-order), so the rows arrive
 * best-match first — an invariant the search integration test guards.
 */
async function searchCatalog(auth: AuthContext, input: ExerciseQuery): Promise<unknown> {
  const { data, error } = await auth.supabase
    .rpc('search_exercises', {
      search_query: input.q,
      filter_equipment: input.equipment,
      filter_muscle: input.muscle,
      filter_category: input.category,
      result_limit: input.limit ?? DEFAULT_LIMIT,
    })
    .select(EXERCISE_COLUMNS);
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
      description:
        'Search the global, public exercise catalog: relevance-ranked, multi-word, ' +
        'alias-aware. Each whitespace token of q is matched against the exercise name ' +
        'and its aliases via trigram word-similarity (token-AND: every token must match), ' +
        'and results rank by summed similarity with canonical base movements first. ' +
        'Params: q (optional term — omit it to browse by filters alone), ' +
        'equipment (optional, matches the equipment list), muscle (optional, matches the ' +
        'primary/legacy muscle list), category (optional exact filter), ' +
        'limit (optional, 1-100, default 25). ' +
        'Returns: an array of catalog rows (id, name, category, muscles, equipment, ' +
        'aliases, is_canonical, license, license_author), best match first.',
      inputSchema,
      annotations: { readOnlyHint: true },
    },
    async (input) => jsonResult(await searchCatalog(auth, input)),
  );
}
