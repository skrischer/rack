package de.rack.app.ui.session

import de.rack.app.ui.theme.SupersetKind

/**
 * One tickable step in a guided session: a single set of one exercise, in the
 * order the player walks through them. [setIndex] is 0-based within the exercise
 * (not the session); [totalSets] is the exercise's target set count so the UI can
 * show "set N of M". [kind] carries the exercise's group role so the player can
 * render the superset/circuit rotation cue. [target], [rir], and [cue] are the same
 * read-only plan-display data the static day view shows, surfaced here so the player
 * reuses the Phase 3/Phase 7 display data instead of re-reading it. Derived from the
 * position-ordered `plan_exercises`; not stored.
 */
data class SessionStep(
    val planExerciseId: String,
    val exerciseId: String,
    val name: String,
    val kind: SupersetKind,
    val setIndex: Int,
    val totalSets: Int,
    val target: String? = null,
    val rir: Int? = null,
    val cue: String? = null,
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
 * The session player's observable state: the [focused] step (the exercise + set in
 * focus), the [remaining] steps still to tick after it (in player order), the
 * already-[done] steps, the per-exercise working [entries] keyed by
 * `plan_exercise_id`, and the per-exercise last-logged [references] (the "last time"
 * summary line) shown for the focused exercise. When [focused] is null every step has
 * been ticked and the session is finished: [summary] then holds the per-exercise
 * sets-done/volume aggregation and [saveState] the confirm-save phase. All
 * stepping/rotation/aggregation lives in the ViewModel; the screen renders this state
 * and emits events only.
 */
data class SessionPlayerUiState(
    val focused: SessionStep? = null,
    val remaining: List<SessionStep> = emptyList(),
    val done: List<SessionStep> = emptyList(),
    val entries: Map<String, ExerciseEntries> = emptyMap(),
    val references: Map<String, String> = emptyMap(),
    val summary: SessionSummary? = null,
    val saveState: SessionSaveState = SessionSaveState.IDLE,
) {
    /** True once the last step has been ticked and no step remains in focus. */
    val isFinished: Boolean get() = focused == null

    /** The working entries for [planExerciseId], or empty defaults if untouched. */
    fun entriesFor(planExerciseId: String): ExerciseEntries = entries[planExerciseId] ?: ExerciseEntries()

    /** The "last time" reference summary for [planExerciseId], or null if none logged. */
    fun referenceFor(planExerciseId: String): String? = references[planExerciseId]

    /**
     * The name of the exercise the player rotates to next within the focused step's
     * superset/circuit group, shown as the "Next: <exercise>" cue. Null for a
     * standalone exercise or when the next remaining step is the same exercise (the
     * round has not rotated yet), so the cue appears only for an actual hand-off.
     */
    val rotationCueName: String?
        get() {
            val current = focused?.takeIf { it.kind != SupersetKind.NONE } ?: return null
            val next = remaining.firstOrNull() ?: return null
            return next.name.takeIf { next.planExerciseId != current.planExerciseId }
        }
}
