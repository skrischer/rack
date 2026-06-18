package de.rack.app.data

import de.rack.app.data.local.SessionDraftDao
import de.rack.app.data.local.SessionDraftEntity
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * The in-progress guided session restored from / saved to the local cache: the
 * [doneCount] cursor (how many steps were ticked) and the per-exercise working
 * [entries] (kg, RIR, per-set reps) keyed by `plan_exercise_id`. The step sequence
 * itself is rebuilt deterministically from the day's exercises, so only the cursor and
 * the entries need persisting.
 */
data class SessionDraft(
    val doneCount: Int,
    val entries: Map<String, SessionDraftEntries>,
)

/** The serialized working entries for one exercise: its single kg/RIR plus per-set reps. */
@Serializable
data class SessionDraftEntries(
    val weight: String = "",
    val rir: String = "",
    val reps: Map<Int, String> = emptyMap(),
)

/**
 * Persists the in-progress guided session to the local cache so it survives
 * backgrounding / process death (offline-resilient logging per the constitution). One
 * draft per `plan_day`; the entries map is stored as JSON. The draft is cleared on
 * confirm (save) or abandon — it is never a mirror of server data, and confirmed sets
 * live in Supabase via the [LoggingRepository], not here.
 */
class SessionDraftRepository(
    private val dao: SessionDraftDao,
    private val json: Json = Json,
) {
    private val entriesSerializer = MapSerializer(String.serializer(), SessionDraftEntries.serializer())

    /** Save (or replace) the draft for [dayId] from its [doneCount] cursor and [entries]. */
    suspend fun save(
        dayId: String,
        doneCount: Int,
        entries: Map<String, SessionDraftEntries>,
    ) {
        dao.upsert(
            SessionDraftEntity(
                dayId = dayId,
                doneCount = doneCount,
                entriesJson = json.encodeToString(entriesSerializer, entries),
            ),
        )
    }

    /** The cached draft for [dayId], or null when no session is in progress. */
    suspend fun load(dayId: String): SessionDraft? =
        dao.find(dayId)?.let { entity ->
            SessionDraft(
                doneCount = entity.doneCount,
                entries = json.decodeFromString(entriesSerializer, entity.entriesJson),
            )
        }

    /** Drop the draft for [dayId] once the session is confirmed (saved) or abandoned. */
    suspend fun clear(dayId: String) = dao.delete(dayId)
}
