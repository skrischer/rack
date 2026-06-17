package de.rack.app.domain

/**
 * An agent-authored visualization artifact (`artifacts`) as surfaced to the list.
 *
 * The camelCase Kotlin representation of an `artifacts` row: metadata plus the
 * [storagePath] pointing at the renderable bytes in the private Storage bucket
 * (fetched via a signed URL by the viewer in #44 — not by the list). [source]
 * distinguishes agent-written artifacts (`'agent'`) from app-written ones so the
 * list can mark them with the volt agent badge.
 */
data class Artifact(
    val id: String,
    val name: String?,
    val type: String?,
    val storagePath: String?,
    val source: String,
    val createdAt: String,
    val updatedAt: String,
) {
    /** True when the artifact was written by the agent (`source='agent'`). */
    val isAgentAuthored: Boolean get() = source == AGENT_SOURCE

    private companion object {
        const val AGENT_SOURCE = "agent"
    }
}
