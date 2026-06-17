package de.rack.app.data

import de.rack.app.domain.ChangeEvent
import de.rack.app.domain.RealtimeChange
import de.rack.app.domain.SetLogChange
import de.rack.app.domain.SyncedTable
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * The single Realtime access point for the user-owned training tables. It opens a
 * per-user [RealtimeChannel] over `plans`, `plan_days`, `plan_exercises`, and
 * `set_logs`, authorizes it with the signed-in user's JWT (the supabase-kt
 * channel-auth call [RealtimeChannel.updateAuth]) so RLS scopes the stream to that
 * user — anon key + user JWT only, never a service-role key — and exposes incoming
 * row changes as a cold [Flow] of [RealtimeChange]. All Realtime access lives here;
 * no Supabase or Realtime call exists in any Composable. Reconciliation/highlight
 * (Phase 4's later steps) consume [changes]; this layer only delivers the stream.
 *
 * On every JWT refresh the channel is torn down and rebuilt against the new token
 * ([flatMapLatest] over the distinct access token), re-authorizing and resubscribing
 * so the stream never goes quiet after a token rotation. Subscription is bound to
 * the collector: cancelling collection unsubscribes the channel.
 */
class RealtimeRepository(
    private val client: SupabaseClient,
) {
    /**
     * Cold stream of row changes on the four synced tables, scoped to the
     * signed-in user. Re-authorizes and resubscribes on each JWT refresh; emits
     * nothing while signed out. Collect within a lifecycle-bound scope so the
     * channel is unsubscribed when the screen/app leaves the foreground.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun changes(): Flow<RealtimeChange> = accessTokens().flatMapLatest { token -> channelChanges(token) }

    /**
     * The [changes] stream narrowed to `set_logs` and decoded into typed
     * [SetLogChange]s, the only training table the app writes optimistically.
     * Consumers reconcile each change by primary key (last-write-wins); a payload
     * whose row id or columns cannot be decoded is dropped rather than crashing
     * the stream.
     */
    fun setLogChanges(): Flow<SetLogChange> =
        changes()
            .filter { change -> change.table == SyncedTable.SET_LOGS }
            .mapNotNull { change -> change.toSetLogChange() }

    /** Distinct access tokens for the current session; re-emits on JWT refresh. */
    private fun accessTokens(): Flow<String> =
        client.auth.sessionStatus
            .mapNotNull { status -> (status as? SessionStatus.Authenticated)?.session?.accessToken }
            .distinctUntilChanged()

    /**
     * A channel authorized with [token] and subscribed over the four tables; emits
     * each table's merged changes until cancelled, then unsubscribes the channel.
     */
    private fun channelChanges(token: String): Flow<RealtimeChange> =
        channelFlow {
            val channel = client.realtime.channel(CHANNEL_TOPIC)
            // Build and collect every table's change flow up front: creating the
            // flow registers the table in the channel's Postgres-changes binding
            // list, which `subscribe` reads into the JOIN payload. Doing this before
            // `subscribe` (not inside a launched collector that races the JOIN)
            // guarantees all four tables are part of the subscription.
            SyncedTable.entries
                .map { table -> tableChanges(channel, table) }
                .forEach { flow -> launch { flow.collect(::send) } }
            channel.updateAuth(token)
            channel.subscribe(blockUntilSubscribed = true)
            try {
                awaitCancellation()
            } finally {
                withContext(NonCancellable) { channel.unsubscribe() }
            }
        }

    /** The [RealtimeChange] flow for a single [table] on [channel]. */
    private fun tableChanges(
        channel: RealtimeChannel,
        table: SyncedTable,
    ): Flow<RealtimeChange> =
        channel.postgresChangeFlow<PostgresAction>(schema = PUBLIC_SCHEMA) {
            this.table = table.tableName
        }.map { action -> action.toChange(table) }

    private companion object {
        const val CHANNEL_TOPIC = "training:user"
        const val PUBLIC_SCHEMA = "public"
    }
}

/** Map a raw Postgres change to the domain [RealtimeChange], reading id/source/updated_at. */
private fun PostgresAction.toChange(table: SyncedTable): RealtimeChange {
    val (event, record) =
        when (this) {
            is PostgresAction.Insert -> ChangeEvent.INSERT to record
            is PostgresAction.Update -> ChangeEvent.UPDATE to record
            is PostgresAction.Delete -> ChangeEvent.DELETE to oldRecord
            is PostgresAction.Select -> ChangeEvent.UPDATE to record
        }
    return RealtimeChange(
        table = table,
        event = event,
        rowId = record.string("id"),
        source = record.string("source"),
        updatedAt = record.string("updated_at"),
        record = record,
    )
}

/** Read a string column from a row payload, tolerating a missing/non-string field. */
private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

/** Lenient decoder for Realtime row payloads (they carry columns the DTO ignores). */
private val realtimeJson = Json { ignoreUnknownKeys = true }

/**
 * Decode a `set_logs` [RealtimeChange] into a typed [SetLogChange], or `null` when
 * the payload cannot be read into a [SetLogDto] (a malformed row is skipped rather
 * than breaking the live stream).
 */
private fun RealtimeChange.toSetLogChange(): SetLogChange? =
    runCatching { realtimeJson.decodeFromJsonElement(SetLogDto.serializer(), record) }
        .getOrNull()
        ?.let { dto -> SetLogChange(log = dto.toDomain(), event = event, source = source) }
