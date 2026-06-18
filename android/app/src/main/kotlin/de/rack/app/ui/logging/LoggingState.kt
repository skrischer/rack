package de.rack.app.ui.logging

import de.rack.app.domain.ChangeEvent
import de.rack.app.domain.SetLog
import de.rack.app.domain.SetLogChange
import de.rack.app.domain.WeightUnit

/**
 * The logging UI state for a single exercise: the inputs being edited, the loaded
 * history (most-recent first, including any optimistic entry), the set of row ids
 * currently flagged for an agent-edit highlight, and transient logging flags.
 * Held per `plan_exercise_id` in [LoggingUiState].
 */
data class ExerciseLogState(
    val setCount: Int,
    val weightInput: String = "",
    val repsInputs: List<String> = List(setCount) { "" },
    val history: List<SetLog> = emptyList(),
    val highlightedIds: Set<String> = emptySet(),
    val historyExpanded: Boolean = false,
    val logging: Boolean = false,
    val error: String? = null,
) {
    /** The most recent logged entry, shown as the "last time" summary. */
    val lastLog: SetLog? get() = history.firstOrNull()

    /** True when at least one input carries a value worth logging. */
    val hasInput: Boolean
        get() = weightInput.isNotBlank() || repsInputs.any { it.isNotBlank() }

    /** True when a new log may start: inputs present and none already in flight. */
    val canLog: Boolean get() = hasInput && !logging

    /** Apply the optimistic [entry] to the top of history and clear the inputs. */
    fun withOptimistic(entry: SetLog): ExerciseLogState =
        copy(
            history = listOf(entry) + history,
            weightInput = "",
            repsInputs = List(setCount) { "" },
            logging = true,
            error = null,
        )

    /** Replace the optimistic entry [optimisticId] with the [persisted] server row. */
    fun reconciled(
        optimisticId: String,
        persisted: SetLog,
    ): ExerciseLogState = copy(history = replaceEntry(optimisticId, persisted), logging = false, error = null)

    /** Keep the optimistic entry (it is cached) but surface the offline notice. */
    fun failed(error: Throwable): ExerciseLogState =
        copy(logging = false, error = error.message?.takeIf(String::isNotBlank) ?: CACHED_MESSAGE)

    /** Swap the history entry whose id matches [id] for [replacement]. */
    fun replaceEntry(
        id: String,
        replacement: SetLog,
    ): List<SetLog> = history.map { if (it.id == id) replacement else it }

    /**
     * Reconcile an incoming Realtime [change] into this exercise's history by
     * primary key (last-write-wins): a delete drops the row, an insert/update
     * upserts it without duplicating the app's own echo. Highlighting is decided
     * separately by the transient [de.rack.app.domain.HighlightTracker] (driven by
     * `source`), so reconciliation only touches the row data here.
     */
    fun applyRealtimeChange(change: SetLogChange): ExerciseLogState {
        val id = change.log.id
        if (change.event == ChangeEvent.DELETE) {
            return copy(history = history.filterNot { it.id == id })
        }
        return copy(history = upsertHistory(change.log))
    }

    /** Project the transient agent-edit [ids] onto this exercise's own rows. */
    fun withHighlighted(ids: Set<String>): ExerciseLogState =
        copy(highlightedIds = ids.intersect(history.map(SetLog::id).toSet()))

    /** Upsert [entry] into history by id, keeping the list most-recent-first by `loggedAt`. */
    private fun upsertHistory(entry: SetLog): List<SetLog> =
        (history.filterNot { it.id == entry.id } + entry).sortedByDescending(SetLog::loggedAt)

    private companion object {
        const val CACHED_MESSAGE = "Saved offline. It will sync when you reconnect."
    }
}

/**
 * The logging state for every exercise on screen, keyed by `plan_exercise_id`, plus
 * the selected [weightUnit] every weight surface displays in and converts entries from
 * (storage stays canonical kg).
 */
data class LoggingUiState(
    val byExercise: Map<String, ExerciseLogState> = emptyMap(),
    val weightUnit: WeightUnit = WeightUnit.KG,
)
