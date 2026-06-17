package de.rack.app.ui.keys

/*
 * Pure builders for the onboarding connection details, derived from the single
 * rack-MCP base URL the app already uses for its admin calls (BuildConfig
 * MCP_BASE_URL, surfaced via the repository). Keeping these out of Composables
 * lets the snippet be generated from the same endpoint constant — never a
 * user-entered field — and matches the no-logic-in-Composables rule.
 */

/** Streamable HTTP transport path the rack-MCP serves tool calls from. */
private const val MCP_TRANSPORT_PATH = "/mcp"

/** Server name a client config keys the rack entry under. */
private const val MCP_SERVER_NAME = "rack"

/**
 * The hosted Streamable-HTTP endpoint an MCP client connects to: the build-config
 * base URL plus the rack-MCP transport path. Trailing slashes on the base are
 * trimmed so the result has exactly one path separator.
 */
fun mcpEndpointUrl(baseUrl: String): String = "${baseUrl.trimEnd('/')}$MCP_TRANSPORT_PATH"

/**
 * A single generic Streamable-HTTP MCP-client config snippet, ready to paste into
 * a client's `mcpServers` block. It carries the resolved [endpointUrl] and the
 * freshly-minted [plaintext] as a bearer token — the same header the rack-MCP
 * tool-call path expects. The plaintext lives only in the transient reveal state,
 * so the snippet is built on demand and never persisted.
 */
fun mcpClientConfigSnippet(
    endpointUrl: String,
    plaintext: String,
): String =
    """
    {
      "mcpServers": {
        "$MCP_SERVER_NAME": {
          "type": "streamableHttp",
          "url": "$endpointUrl",
          "headers": {
            "Authorization": "Bearer $plaintext"
          }
        }
      }
    }
    """.trimIndent()
