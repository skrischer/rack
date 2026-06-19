/**
 * Write tools over the caller's plans, plan days, plan exercises, and set logs:
 * create, update, and delete, each scoped to the resolved user by RLS
 * (`user_id = auth.uid()`). Every create and update sets `source='agent'`
 * explicitly so Phase 4 can highlight agent edits; `updated_at` is advanced by
 * the Phase 1 BEFORE UPDATE trigger. Deletes are hard deletes scoped by RLS.
 *
 * No tool accepts a `user_id` or account selector: identity comes solely from
 * the {@link AuthContext}, and creates inject `user_id` from it. A create/update
 * targeting another user's parent row fails the RLS `with check`; an update or
 * delete by an id the caller does not own returns a not-found error phrased
 * identically to a genuinely missing row, so no cross-user existence leaks. See
 * docs/specs/spec-rack-mcp.md.
 */

import { McpServer } from '@modelcontextprotocol/sdk/server/mcp.js';
import type { CallToolResult } from '@modelcontextprotocol/sdk/types.js';
import { z, type ZodRawShape } from 'zod';

import type { AuthContext } from '../auth.js';
import { errorResult, jsonResult } from './result.js';

/** Columns returned for the affected row, per table, after a write. */
const RETURNING: Record<string, string> = {
  plans: 'id, name, kind, source, created_at, updated_at',
  plan_days: 'id, plan_id, position, title, focus, tag, source, updated_at',
  plan_exercises:
    'id, day_id, exercise_id, position, sets, rep_min, rep_max, rir_low, rir_high, ' +
    'rest_seconds, cue, superset_id, group_type, source, updated_at',
  set_logs: 'id, plan_exercise_id, date, weight, reps, rir, logged_at, source, updated_at',
};

/** A validated UUID for the id of a row to update or delete. */
const uuid = (field: string): z.ZodUUID => z.uuid({ message: `${field} must be a UUID` });

/**
 * Inserts a new row for the resolved user with `source='agent'` and the
 * caller's `user_id` injected from the auth context (never from input).
 */
async function createRow(
  auth: AuthContext,
  table: string,
  values: Record<string, unknown>,
): Promise<CallToolResult> {
  const { data, error } = await auth.supabase
    .from(table)
    .insert({ ...toColumns(values), user_id: auth.userId, source: 'agent' })
    .select(RETURNING[table])
    .single<Record<string, unknown>>();
  if (error !== null) {
    return errorResult(`failed to create ${table}: ${error.message}`);
  }
  return jsonResult(data);
}

/**
 * Updates a row the caller owns, forcing `source='agent'`; the trigger advances
 * `updated_at`. A missing or non-owned id yields a not-found error.
 */
async function updateRow(
  auth: AuthContext,
  table: string,
  id: string,
  patch: Record<string, unknown>,
): Promise<CallToolResult> {
  const { data, error } = await auth.supabase
    .from(table)
    .update({ ...toColumns(patch), source: 'agent' })
    .eq('id', id)
    .select(RETURNING[table])
    .maybeSingle<Record<string, unknown>>();
  if (error !== null) {
    return errorResult(`failed to update ${table}: ${error.message}`);
  }
  if (data === null) {
    return errorResult(`${table} ${id} not found`);
  }
  return jsonResult(data);
}

/**
 * Hard-deletes a row the caller owns (RLS-scoped). A missing or non-owned id
 * yields the same not-found error, leaking nothing about other users' rows.
 */
async function deleteRow(auth: AuthContext, table: string, id: string): Promise<CallToolResult> {
  const { data, error } = await auth.supabase
    .from(table)
    .delete()
    .eq('id', id)
    .select('id')
    .maybeSingle<{ id: string }>();
  if (error !== null) {
    return errorResult(`failed to delete ${table}: ${error.message}`);
  }
  if (data === null) {
    return errorResult(`${table} ${id} not found`);
  }
  return jsonResult({ deleted: data.id });
}

/** Registers `create_<table>`, validating its input via `createSchema`. */
function registerCreate(
  server: McpServer,
  auth: AuthContext,
  name: string,
  table: string,
  createSchema: z.ZodType,
): void {
  server.registerTool(
    name,
    {
      title: name,
      description:
        `Create one ${table} row for the caller (written with source='agent'). ` +
        `Params: the ${table} fields (Zod-validated; id and user_id are server-assigned, ` +
        `never accepted from the caller). ` +
        `Returns: the created row.`,
      inputSchema: createSchema,
    },
    // The SDK validates `input` against `createSchema` before this fires, so the
    // cast from the SDK's erased `unknown` (a non-generic z.ZodType loses the
    // inferred output type) to a record is sound.
    async (input) => createRow(auth, table, input as Record<string, unknown>),
  );
}

/** Registers `update_<table>`: a required `id` plus the optional patch fields. */
function registerUpdate(
  server: McpServer,
  auth: AuthContext,
  name: string,
  table: string,
  patchShape: ZodRawShape,
): void {
  const schema = z.object({ id: uuid('id'), ...patchShape }).refine(
    (value) => Object.keys(value).length > 1,
    { message: 'at least one field to update is required' },
  );
  server.registerTool(
    name,
    {
      title: name,
      description:
        `Update one of the caller's ${table} rows by id (forces source='agent'). ` +
        `Params: id (required UUID) plus at least one ${table} field to change (Zod-validated). ` +
        `Returns: the updated row; errors if the id is not found or not owned.`,
      inputSchema: schema,
    },
    async ({ id, ...patch }) => updateRow(auth, table, id, patch),
  );
}

/** Registers `delete_<table>`: a required `id`, hard-deleted under RLS. */
function registerDelete(
  server: McpServer,
  auth: AuthContext,
  name: string,
  table: string,
): void {
  server.registerTool(
    name,
    {
      title: name,
      description:
        `Delete one of the caller's ${table} rows by id (hard delete, RLS-scoped). ` +
        `Params: id (required UUID). ` +
        `Returns: { deleted: <id> }; errors if the id is not found or not owned.`,
      inputSchema: { id: uuid('id') },
    },
    async ({ id }) => deleteRow(auth, table, id),
  );
}

/** Optional integer-array reps (each non-negative), shared by create/update. */
const repsField = z
  .array(z.number().int().min(0, { message: 'reps must be non-negative integers' }))
  .optional();

/** The named `plan_group_type` enum values, shared by create/update. */
const groupTypeField = z.enum(['superset', 'circuit']).optional();

/**
 * Rep-range invariant; only constrains when both bounds are present. Exported so
 * the nested `create_plan` input refines exactly like the per-row create.
 */
export const repRangeOk = (v: { repMin?: number; repMax?: number }): boolean =>
  v.repMin === undefined || v.repMax === undefined || v.repMin <= v.repMax;

/** RIR-range invariant; only constrains when both bounds are present. */
export const rirRangeOk = (v: { rirLow?: number; rirHigh?: number }): boolean =>
  v.rirLow === undefined || v.rirHigh === undefined || v.rirLow <= v.rirHigh;

/**
 * Day fields shared by `create_plan_days` and the nested `create_plan` input
 * (the parent `planId` is implicit when a day is nested under its plan).
 */
export const planDayFields = {
  position: z.number().int().min(0),
  title: z.string().min(1).optional(),
  focus: z.string().min(1).optional(),
  tag: z.string().min(1).optional(),
};

/**
 * Phase 15 typed prescription + per-day grouping fields shared by
 * `create_plan_exercises` and the nested `create_plan` input (the parent `dayId`
 * is implicit when an exercise is nested under its day). The legacy
 * `target`/`rir`/`superset_label` fields are intentionally absent.
 */
export const planExerciseFields = {
  exerciseId: uuid('exerciseId'),
  position: z.number().int().min(0),
  sets: z.number().int().positive().optional(),
  repMin: z.number().int().min(0).optional(),
  repMax: z.number().int().min(0).optional(),
  rirLow: z.number().int().min(0).optional(),
  rirHigh: z.number().int().min(0).optional(),
  restSeconds: z.number().int().min(0).optional(),
  cue: z.string().min(1).optional(),
  supersetId: z.number().int().min(0).optional(),
  groupType: groupTypeField,
};

/** Per-table create schemas (the id is server-assigned, never accepted). */
const CREATE_SCHEMAS = {
  plans: z.object({
    name: z.string().min(1, { message: 'name must not be empty' }),
    kind: z.string().min(1).optional(),
  }),
  plan_days: z.object({ planId: uuid('planId'), ...planDayFields }),
  plan_exercises: z
    .object({ dayId: uuid('dayId'), ...planExerciseFields })
    .refine(repRangeOk, { message: 'repMin must be less than or equal to repMax', path: ['repMin'] })
    .refine(rirRangeOk, { message: 'rirLow must be less than or equal to rirHigh', path: ['rirLow'] }),
  set_logs: z.object({
    planExerciseId: uuid('planExerciseId'),
    date: z.iso.date({ message: 'date must be an ISO date (YYYY-MM-DD)' }).optional(),
    weight: z.number().min(0).optional(),
    reps: repsField,
    rir: z.number().int().min(0).optional(),
  }),
};

/** Per-table update patch shapes: every field optional, foreign keys excluded. */
const UPDATE_SHAPES = {
  plans: { name: z.string().min(1).optional(), kind: z.string().min(1).optional() },
  plan_days: {
    position: z.number().int().min(0).optional(),
    title: z.string().min(1).optional(),
    focus: z.string().min(1).optional(),
    tag: z.string().min(1).optional(),
  },
  plan_exercises: {
    position: z.number().int().min(0).optional(),
    sets: z.number().int().positive().optional(),
    repMin: z.number().int().min(0).optional(),
    repMax: z.number().int().min(0).optional(),
    rirLow: z.number().int().min(0).optional(),
    rirHigh: z.number().int().min(0).optional(),
    restSeconds: z.number().int().min(0).optional(),
    cue: z.string().min(1).optional(),
    supersetId: z.number().int().min(0).optional(),
    groupType: groupTypeField,
  },
  set_logs: {
    date: z.iso.date({ message: 'date must be an ISO date (YYYY-MM-DD)' }).optional(),
    weight: z.number().min(0).optional(),
    reps: repsField,
    rir: z.number().int().min(0).optional(),
  },
} as const;

/** Maps camelCase tool input keys to their snake_case database columns. */
function toColumns(input: Record<string, unknown>): Record<string, unknown> {
  const rename: Record<string, string> = {
    planId: 'plan_id',
    dayId: 'day_id',
    exerciseId: 'exercise_id',
    planExerciseId: 'plan_exercise_id',
    repMin: 'rep_min',
    repMax: 'rep_max',
    rirLow: 'rir_low',
    rirHigh: 'rir_high',
    restSeconds: 'rest_seconds',
    supersetId: 'superset_id',
    groupType: 'group_type',
  };
  return Object.fromEntries(
    Object.entries(input).map(([key, value]) => [rename[key] ?? key, value]),
  );
}

/** Registers create/update/delete for one table under its tool-name prefix. */
function registerTable(
  server: McpServer,
  auth: AuthContext,
  table: keyof typeof CREATE_SCHEMAS,
): void {
  registerCreate(server, auth, `create_${table}`, table, CREATE_SCHEMAS[table]);
  registerUpdate(server, auth, `update_${table}`, table, UPDATE_SHAPES[table]);
  registerDelete(server, auth, `delete_${table}`, table);
}

/** Registers every write tool (create/update/delete across the four tables). */
export function registerWriteTools(server: McpServer, auth: AuthContext): void {
  registerTable(server, auth, 'plans');
  registerTable(server, auth, 'plan_days');
  registerTable(server, auth, 'plan_exercises');
  registerTable(server, auth, 'set_logs');
}
