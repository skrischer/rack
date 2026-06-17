package de.rack.app.data

import de.rack.app.domain.ChangeEvent
import de.rack.app.domain.RealtimeChange
import de.rack.app.domain.RealtimeEvent
import de.rack.app.domain.SetLogChange
import de.rack.app.domain.SetLogEvent
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** Backoff before re-subscribing a dropped channel, so a flapping socket is not hammered. */
private const val RECONNECT_DELAY_MS = 1_000L

/**
 * The single Realtime access point for the user-owned training tables. It opens a
 * per-user [RealtimeChannel] over `plans`, `plan_days`, `plan_exercises`, and
 * `set_logs`, authorizes it with the signed-in user's JWT (the supabase-kt
 * channel-auth call [RealtimeChannel.updateAuth]) so RLS scopes the stream to that
 * user — anon key + user JWT only, never a service-role key — and exposes the
 * lifecycle as a cold [Flow] of [RealtimeEvent]: a [RealtimeEvent.Resync] marker on
 * every (re)subscribe followed by the live [RealtimeEvent.Row] changes. All
 * Realtime access lives here; no Supabase or Realtime call exists in any Composable.
 *
 * Lifecycle and reconnect:
 * - Subscription is bound to the collector — a single channel per collection, and
 *   cancelling collection unsubscribes the channel. Collect from a lifecycle-bound
 *   scope (foreground only) so the channel is dropped on background and rebuilt on
 *   return; that resume re-enters [channelEvents] and re-emits a [RealtimeEvent.Resync].
 * - On every JWT refresh the channel is torn down and rebuilt against the new token
 *   ([flatMapLatest] over the distinct access token), re-authorizing and resubscribing.
 * - A channel that drops to [RealtimeChannel.Status.UNSUBSCRIBED] after being
 *   subscribed (a lost socket or an auth-revoked channel) is treated as a reconnect:
 *   it is re-subscribed and a fresh [RealtimeEvent.Resync] is emitted, so server
 *   state is re-read before the live stream is trusted again.
 *
 * Catch-up after a disconnect is a full repository re-read on [RealtimeEvent.Resync]
 * (Supabase Postgres Changes does not replay a missed-event backlog), not a backlog
 * replay; changes that arrived while disconnected reconcile as current state.
 */
class RealtimeRepository(
    private val client: SupabaseClient,
) {
    /**
     * Cold stream of [RealtimeEvent]s scoped to the signed-in user: a
     * [RealtimeEvent.Resync] on each (re)subscribe, then the live row changes.
     * Re-authorizes and resubscribes on each JWT refresh; emits nothing while
     * signed out. Collect within a lifecycle-bound scope so the channel is
     * unsubscribed when the screen/app leaves the foreground.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun events(): Flow<RealtimeEvent> = accessTokens().flatMapLatest { token -> channelEvents(token) }

    /**
     * The [events] stream specialized for the logging screen: a single collection
     * (one channel) that emits a [SetLogEvent.Resync] on every (re)subscribe and a
     * decoded [SetLogEvent.Change] per `set_logs` row change — the only training
     * table the app writes optimistically. A payload whose row id or columns cannot
     * be decoded is dropped rather than crashing the stream.
     */
    fun setLogEvents(): Flow<SetLogEvent> =
        events().mapNotNull { event ->
            when (event) {
                RealtimeEvent.Resync -> SetLogEvent.Resync
                is RealtimeEvent.Row -> event.change.toSetLogEvent()
            }
        }

    /** A `set_logs` row [RealtimeChange] decoded into a [SetLogEvent.Change], or `null`. */
    private fun RealtimeChange.toSetLogEvent(): SetLogEvent.Change? =
        takeIf { table == SyncedTable.SET_LOGS }
            ?.toSetLogChange()
            ?.let(SetLogEvent::Change)

    /** Distinct access tokens for the current session; re-emits on JWT refresh. */
    private fun accessTokens(): Flow<String> =
        client.auth.sessionStatus
            .mapNotNull { status -> (status as? SessionStatus.Authenticated)?.session?.accessToken }
            .distinctUntilChanged()

    /**
     * A channel authorized with [token] and subscribed over the four tables. Emits
     * a [RealtimeEvent.Resync] on each (re)subscribe and a [RealtimeEvent.Row] per
     * change; re-subscribes after an unexpected drop (lost socket / auth-revoke).
     * Unsubscribes the channel when collection is cancelled.
     */
    private fun channelEvents(token: String): Flow<RealtimeEvent> =
        channelFlow {
            val channel = client.realtime.channel(CHANNEL_TOPIC)
            // Build and collect every table's change flow up front: creating the
            // flow registers the table in the channel's Postgres-changes binding
            // list, which `subscribe` reads into the JOIN payload. Doing this before
            // `subscribe` (not inside a launched collector that races the JOIN)
            // guarantees all four tables are part of the subscription.
            SyncedTable.entries
                .map { table -> tableChanges(channel, table) }
                .forEach { flow -> launch { flow.collect { change -> send(RealtimeEvent.Row(change)) } } }
            // Emit a Resync the first time the channel subscribes and on every
            // reconnect after it drops, so the consumer re-reads server state before
            // trusting the live stream again.
            launch { channel.emitResyncOnSubscribe { send(RealtimeEvent.Resync) } }
            channel.updateAuth(token)
            channel.subscribe(blockUntilSubscribed = true)
            // A drop to UNSUBSCRIBED after a successful subscribe (lost socket or an
            // auth-revoked channel) is a reconnect: re-auth and re-subscribe.
            launch { channel.reconnectOnDrop(token) }
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

/**
 * Invoke [emit] once for every transition of this channel into
 * [RealtimeChannel.Status.SUBSCRIBED] — the initial subscribe and each reconnect —
 * so the consumer performs a catch-up re-read on every (re)subscribe.
 */
private suspend fun RealtimeChannel.emitResyncOnSubscribe(emit: suspend () -> Unit) {
    var wasSubscribed = false
    status.collect { current ->
        val subscribed = current == RealtimeChannel.Status.SUBSCRIBED
        if (subscribed && !wasSubscribed) emit()
        wasSubscribed = subscribed
    }
}

/**
 * Re-authorize with [token] and re-subscribe whenever this channel drops to
 * [RealtimeChannel.Status.UNSUBSCRIBED] after having been subscribed — a lost
 * socket or an auth-revoked channel. A short backoff avoids hammering a flapping
 * connection; the resubscribe re-arms [emitResyncOnSubscribe], which re-reads state.
 */
private suspend fun RealtimeChannel.reconnectOnDrop(token: String) {
    var wasSubscribed = false
    status.collect { current ->
        if (current == RealtimeChannel.Status.SUBSCRIBED) {
            wasSubscribed = true
        } else if (current == RealtimeChannel.Status.UNSUBSCRIBED && wasSubscribed) {
            wasSubscribed = false
            delay(RECONNECT_DELAY_MS)
            updateAuth(token)
            subscribe(blockUntilSubscribed = true)
        }
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
