package de.rack.app.ui.session

import de.rack.app.domain.WeightUnit
import de.rack.app.ui.theme.SupersetKind

/**
 * One tickable step in a guided session: a single set of one exercise, in the
 * order the player walks through them. [setIndex] is 0-based within the exercise
 * (not the session); [totalSets] is the exercise's target set count so the UI can
 * show "set N of M". [kind] carries the exercise's group role so the player can
 * render the superset/circuit rotation cue. Derived from the position-ordered
 * `plan_exercises`; not stored. The plan prescription is rendered from the static
 * [SessionExerciseBlock], not per step.
 */
data class SessionStep(
    val planExerciseId: String,
    val exerciseId: String,
    val name: String,
    val kind: SupersetKind,
    val setIndex: Int,
    val totalSets: Int,
)

/**
 * The working entries for one exercise across the whole session: a single per-set
 * [reps] map (set index -> entered reps text) plus one per-exercise [weight] and
 * one per-exercise [rir] input, because `set_logs` stores a scalar `weight`, a
 * scalar `rir`, and an array `reps[]` per `plan_exercise_id` (architecture.md). The
 * inputs are held as raw strings so the screen can pre-fill and edit them before a
 * set is ticked; parsing happens at persist time (out of scope for this phase).
 */
data class ExerciseEntries(
    val weight: String = "",
    val rir: String = "",
    val reps: Map<Int, String> = emptyMap(),
) {
    fun withReps(
        setIndex: Int,
        value: String,
    ): ExerciseEntries = copy(reps = reps + (setIndex to value))
}

/** The persist phase of the finished session's confirm-save to `set_logs`. */
enum class SessionSaveState {
    /** Not saving: the summary is shown awaiting confirm or abandon. */
    IDLE,

    /** The confirm write to `set_logs` is in flight. */
    SAVING,

    /** Every logged exercise has been written; the player can close. */
    SAVED,

    /** The confirm write failed; the summary stays so the user can retry. */
    ERROR,
}

/**
 * The session player's observable state for the set-table layout: the static
 * per-exercise [blocks] (one card each, with the superset/circuit group header on the
 * first member), the already-ticked [done] sets, the per-exercise working [entries] keyed
 * by `plan_exercise_id`, the per-set "Vorher" [previous] strings, and the per-exercise
 * last-logged [references]. Each set is ticked individually in its card; [finished] flips
 * when the user ends the session, when [summary] holds the per-exercise sets-done/volume
 * aggregation and [saveState] the confirm-save phase. All grouping/aggregation lives in
 * the ViewModel; the screen renders this state and emits events only.
 */
data class SessionPlayerUiState(
    val blocks: List<SessionExerciseBlock> = emptyList(),
    val done: List<SessionStep> = emptyList(),
    val entries: Map<String, ExerciseEntries> = emptyMap(),
    val previous: Map<String, List<String>> = emptyMap(),
    val references: Map<String, String> = emptyMap(),
    val finished: Boolean = false,
    val summary: SessionSummary? = null,
    val saveState: SessionSaveState = SessionSaveState.IDLE,
    val weightUnit: WeightUnit = WeightUnit.KG,
) {
    /** True once the user has ended the session, so the confirm-save summary is shown. */
    val isFinished: Boolean get() = finished

    /** The total number of tickable sets across every exercise in the session. */
    val totalSets: Int get() = blocks.sumOf(SessionExerciseBlock::setCount)

    /** The working entries for [planExerciseId], or empty defaults if untouched. */
    fun entriesFor(planExerciseId: String): ExerciseEntries = entries[planExerciseId] ?: ExerciseEntries()

    /** The "last time" reference summary for [planExerciseId], or null if none logged. */
    fun referenceFor(planExerciseId: String): String? = references[planExerciseId]

    /** The per-set "Vorher" strings for [planExerciseId]; empty entries render as "—". */
    fun previousFor(planExerciseId: String): List<String> = previous[planExerciseId].orEmpty()

    /** Whether the [setIndex]-th set of [planExerciseId] has been ticked. */
    fun isSetDone(
        planExerciseId: String,
        setIndex: Int,
    ): Boolean = done.any { it.planExerciseId == planExerciseId && it.setIndex == setIndex }
}

/**
 * The data the running set-table view renders, bundled so the body takes one parameter:
 * the live [session] state and the elapsed [durationSeconds] from the Phase-8 timer.
 */
data class SessionRunningContent(
    val session: SessionPlayerUiState,
    val durationSeconds: Int,
)
