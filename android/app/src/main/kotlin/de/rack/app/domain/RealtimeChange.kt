package de.rack.app.domain

import kotlinx.serialization.json.JsonObject

/**
 * One row change delivered over the per-user Supabase Realtime channel for the
 * synced training tables (`plans`, `plan_days`, `plan_exercises`, `set_logs`).
 *
 * The channel is JWT-authorized so RLS scopes the stream to the signed-in user;
 * every payload carries the constitution-guaranteed [source] ('app' | 'agent')
 * and [updatedAt], which Phase 4's later steps use to highlight agent edits and
 * reconcile against the user's own optimistic writes by [rowId] (last-write-wins).
 * The full [record] is kept as the raw row so each consuming layer can decode it
 * into its own table DTO; for a delete it is the old row image (the only payload
 * a delete carries), so [rowId] still identifies the row to drop.
 */
data class RealtimeChange(
    val table: SyncedTable,
    val event: ChangeEvent,
    val rowId: String?,
    val source: String?,
    val updatedAt: String?,
    val record: JsonObject,
)

/** The Postgres mutation kind behind a [RealtimeChange]. */
enum class ChangeEvent {
    INSERT,
    UPDATE,
    DELETE,
}

/** The `source` attribution every mutation carries; the highlight signal for the UI. */
const val SOURCE_AGENT = "agent"

/**
 * A decoded `set_logs` Realtime change: the [SetLog] the payload carries (for a
 * delete it is the dropped row's last image, so [SetLog.id] still identifies it),
 * the mutation [event], and the [source] attribution. Reconciliation upserts/drops
 * by [SetLog.id] (last-write-wins); [isAgentEdit] is the sole highlight signal, so
 * the app's own `source='app'` echoes are reconciled but never flagged.
 */
data class SetLogChange(
    val log: SetLog,
    val event: ChangeEvent,
    val source: String?,
) {
    /** True only when the latest payload was an agent write — the highlight trigger. */
    val isAgentEdit: Boolean get() = source == SOURCE_AGENT
}

/** The four user-owned training tables this phase subscribes to over Realtime. */
enum class SyncedTable(
    val tableName: String,
) {
    PLANS("plans"),
    PLAN_DAYS("plan_days"),
    PLAN_EXERCISES("plan_exercises"),
    SET_LOGS("set_logs"),
}
