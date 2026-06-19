/**
 * Read tool over the caller's set logs (workout history), filterable by plan
 * exercise and by an inclusive date range on the `date` column. Runs through the
 * per-request user-scoped client so only the resolved user's logs return (RLS);
 * the tool exposes no `user_id` selector. See docs/specs/spec-rack-mcp.md.
 */

import { McpServer } from '@modelcontextprotocol/sdk/server/mcp.js';
import { z } from 'zod';

import type { AuthContext } from '../auth.js';
import { jsonResult } from './result.js';

/** Columns returned for a set-log row (the caller's own, by RLS). */
const SET_LOG_COLUMNS =
  'id, plan_exercise_id, date, weight, reps, rir, logged_at, source, updated_at';

/** Default maximum rows returned when no `limit` is given. */
const DEFAULT_LIMIT = 100;

/** Zod input shape: all filters optional; an empty input returns recent logs. */
const inputSchema = {
  planExerciseId: z.uuid({ message: 'planExerciseId must be a UUID' }).optional(),
  from: z.iso.date({ message: 'from must be an ISO date (YYYY-MM-DD)' }).optional(),
  to: z.iso.date({ message: 'to must be an ISO date (YYYY-MM-DD)' }).optional(),
  limit: z.number().int().positive().max(500).optional(),
} as const;

/** Parsed and validated input for `list_set_logs`. */
type SetLogQuery = {
  planExerciseId?: string;
  from?: string;
  to?: string;
  limit?: number;
};

/** Builds and runs the filtered set-log query under the user-scoped client. */
async function querySetLogs(auth: AuthContext, input: SetLogQuery): Promise<unknown> {
  let query = auth.supabase.from('set_logs').select(SET_LOG_COLUMNS);
  if (input.planExerciseId !== undefined) {
    query = query.eq('plan_exercise_id', input.planExerciseId);
  }
  if (input.from !== undefined) {
    query = query.gte('date', input.from);
  }
  if (input.to !== undefined) {
    query = query.lte('date', input.to);
  }
  const { data, error } = await query
    .order('date', { ascending: false, nullsFirst: false })
    .order('logged_at', { ascending: false })
    .limit(input.limit ?? DEFAULT_LIMIT);
  if (error !== null) {
    throw new Error(`failed to list set logs: ${error.message}`);
  }
  return data;
}

/** Registers `list_set_logs` on the server. */
export function registerSetLogTools(server: McpServer, auth: AuthContext): void {
  server.registerTool(
    'list_set_logs',
    {
      title: 'List set logs',
      description:
        "List the caller's logged sets (workout history), newest first. " +
        'Params: planExerciseId (optional UUID), from/to (optional inclusive ISO dates ' +
        'YYYY-MM-DD), limit (optional, 1-500, default 100). ' +
        'Returns: an array of set-log rows (id, plan_exercise_id, date, weight, reps, rir, ' +
        'logged_at, source, updated_at).',
      inputSchema,
      annotations: { readOnlyHint: true },
    },
    async (input) => jsonResult(await querySetLogs(auth, input)),
  );
}
