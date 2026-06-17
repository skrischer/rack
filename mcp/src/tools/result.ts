/**
 * Shared result helpers for rack-MCP tool handlers.
 *
 * Read tools return their data as a single JSON text block so any MCP client can
 * render it without an out-of-band schema. The data is the resolved user's rows
 * (already RLS-scoped) or public catalog rows; handlers never shape another
 * user's data because the user-scoped client cannot read it. Write tools reuse
 * the same JSON block for the affected row and an {@link errorResult} for a
 * row the caller does not own — phrased identically to a genuinely missing row
 * so it never leaks another user's data existence. See
 * docs/specs/spec-rack-mcp.md.
 */

import type { CallToolResult } from '@modelcontextprotocol/sdk/types.js';

/** Wraps a query payload as a pretty-printed JSON text tool result. */
export function jsonResult(payload: unknown): CallToolResult {
  return { content: [{ type: 'text', text: JSON.stringify(payload, null, 2) }] };
}

/** A structured tool error with `isError` set so MCP clients surface it. */
export function errorResult(message: string): CallToolResult {
  return { content: [{ type: 'text', text: message }], isError: true };
}
