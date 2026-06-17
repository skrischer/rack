package de.rack.app.domain

/**
 * A per-user rack-MCP API key as surfaced to the key-management UI.
 *
 * Mirrors the non-secret display fields the MCP admin list endpoint returns; it
 * never carries the plaintext or the stored hash. The plaintext is shown exactly
 * once at creation via [CreatedApiKey] and lives only in transient ViewModel
 * state, never in this model and never persisted.
 */
data class ApiKey(
    val id: String,
    val name: String?,
    val keyPrefix: String?,
    val createdAt: String,
    val lastUsedAt: String?,
    val revoked: Boolean,
)

/**
 * The result of minting a key: the plaintext returned exactly once by the create
 * endpoint. Held only in memory on the reveal screen and discarded on navigation
 * away; the server cannot return it again.
 */
data class CreatedApiKey(
    val plaintext: String,
)
