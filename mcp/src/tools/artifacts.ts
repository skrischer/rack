/**
 * Tools over the caller's visualization artifacts. `create_artifact` uploads
 * agent-authored renderable bytes to the private `artifacts` Storage bucket and
 * inserts the matching `artifacts` row; `list_artifacts` returns the caller's
 * artifact rows newest first. All access acts as the resolved user (RLS /
 * Storage path policies apply), never the service-role key.
 *
 * The object lands at `{user_id}/{artifact_id}.{ext}` inside the bucket, with the
 * extension derived from the validated MIME enum; the row records the full
 * `artifacts/{user_id}/{artifact_id}.{ext}` path, the `type`, `name`, and
 * `source='agent'`. No `user_id` is accepted from any tool: identity comes
 * solely from the {@link AuthContext}. Input is Zod-validated (bounded MIME
 * allow-list, 2 MB content cap) so oversized or disallowed payloads are rejected
 * at the boundary with no upload or row insert. See
 * docs/specs/spec-visualization-artifacts.md.
 */

import { randomUUID } from 'node:crypto';

import { McpServer } from '@modelcontextprotocol/sdk/server/mcp.js';
import type { CallToolResult } from '@modelcontextprotocol/sdk/types.js';
import { z } from 'zod';

import type { AuthContext } from '../auth.js';
import { errorResult, jsonResult } from './result.js';

/** The private Storage bucket holding agent-authored artifact objects. */
const BUCKET = 'artifacts';

/** Columns returned for a listed artifact row (the caller's own, by RLS). */
const ARTIFACT_COLUMNS = 'id, name, type, storage_path, source, created_at, updated_at';

/** Hard content cap (2 MB) enforced on the decoded bytes at the boundary. */
const MAX_CONTENT_BYTES = 2 * 1024 * 1024;

/** Allowed artifact MIME types mapped to their fixed object extension. */
const TYPE_EXTENSIONS = {
  'text/html': 'html',
  'image/svg+xml': 'svg',
  'image/png': 'png',
} as const;

/** A supported artifact MIME type. */
type ArtifactType = keyof typeof TYPE_EXTENSIONS;

/** Input schema: name, MIME type, and content, with no caller-supplied id. */
const createArtifactShape = {
  name: z.string().min(1, { message: 'name must not be empty' }),
  type: z.enum(
    Object.keys(TYPE_EXTENSIONS) as [ArtifactType, ...ArtifactType[]],
    { message: 'type must be one of text/html, image/svg+xml, image/png' },
  ),
  content: z
    .string()
    .min(1, { message: 'content must not be empty' })
    .refine((value) => Buffer.byteLength(value, 'utf8') <= MAX_CONTENT_BYTES, {
      message: 'content exceeds the 2 MB limit',
    }),
} as const;

const createArtifactSchema = z.object(createArtifactShape).strict();

/** Validated `create_artifact` input. */
type CreateArtifactInput = z.infer<typeof createArtifactSchema>;

/** The row shape returned to the caller after a successful create. */
interface ArtifactRow {
  id: string;
  name: string;
  type: string;
  storage_path: string;
}

/**
 * Uploads the artifact bytes to the user's prefix and inserts its row, both as
 * the resolved user. The bytes go in first so a failed upload never leaves an
 * orphan row; an insert failure is reported with the object already uploaded
 * under the owner's RLS-scoped prefix.
 */
async function createArtifact(
  auth: AuthContext,
  input: CreateArtifactInput,
): Promise<CallToolResult> {
  const artifactId = randomUUID();
  const extension = TYPE_EXTENSIONS[input.type];
  const objectPath = `${auth.userId}/${artifactId}.${extension}`;
  const body = new Blob([input.content], { type: input.type });

  const uploaded = await auth.supabase.storage.from(BUCKET).upload(objectPath, body, {
    contentType: input.type,
  });
  if (uploaded.error !== null) {
    return errorResult(`failed to upload artifact: ${uploaded.error.message}`);
  }

  const storagePath = `${BUCKET}/${objectPath}`;
  const { data, error } = await auth.supabase
    .from('artifacts')
    .insert({
      id: artifactId,
      user_id: auth.userId,
      name: input.name,
      type: input.type,
      storage_path: storagePath,
      source: 'agent',
    })
    .select('id, name, type, storage_path')
    .single<ArtifactRow>();
  if (error !== null) {
    return errorResult(`failed to create artifact: ${error.message}`);
  }
  return jsonResult(data);
}

/**
 * Lists the resolved user's artifact rows newest first. The query runs through
 * the user-scoped client so RLS (`user_id = auth.uid()`) limits the result to
 * the caller's own rows; no `user_id` or account selector is accepted.
 */
async function listArtifacts(auth: AuthContext): Promise<CallToolResult> {
  const { data, error } = await auth.supabase
    .from('artifacts')
    .select(ARTIFACT_COLUMNS)
    .order('created_at', { ascending: false });
  if (error !== null) {
    return errorResult(`failed to list artifacts: ${error.message}`);
  }
  return jsonResult(data);
}

/** Registers the artifact tools (`create_artifact`, `list_artifacts`). */
export function registerArtifactTools(server: McpServer, auth: AuthContext): void {
  server.registerTool(
    'create_artifact',
    {
      title: 'create_artifact',
      description:
        'Upload a renderable visualization artifact (HTML, SVG, or PNG) and record it for the caller.',
      inputSchema: createArtifactSchema,
    },
    async (input) => createArtifact(auth, input),
  );
  server.registerTool(
    'list_artifacts',
    {
      title: 'list_artifacts',
      description: "List the caller's visualization artifacts, newest first.",
      inputSchema: z.object({}).strict(),
    },
    async () => listArtifacts(auth),
  );
}
