package de.rack.app.ui.session

import de.rack.app.ui.theme.SupersetKind

/**
 * One tickable step in a guided session: a single set of one exercise, in the
 * order the player walks through them. [setIndex] is 0-based within the exercise
 * (not the session); [totalSets] is the exercise's target set count so the UI can
 * show "set N of M". [kind] carries the exercise's group role so the player can
 * render the superset/circuit rotation cue. Derived from the position-ordered
 * `plan_exercises`; not stored.
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

/**
 * The session player's observable state: the [focused] step (the exercise + set in
 * focus), the [remaining] steps still to tick after it (in player order), the
 * already-[done] steps, and the per-exercise working [entries] keyed by
 * `plan_exercise_id`. When [focused] is null every step has been ticked and the
 * session is ready for its summary. All stepping/rotation lives in the ViewModel;
 * the screen renders this state and emits events only.
 */
data class SessionPlayerUiState(
    val focused: SessionStep? = null,
    val remaining: List<SessionStep> = emptyList(),
    val done: List<SessionStep> = emptyList(),
    val entries: Map<String, ExerciseEntries> = emptyMap(),
) {
    /** True once the last step has been ticked and no step remains in focus. */
    val isFinished: Boolean get() = focused == null

    /** The working entries for [planExerciseId], or empty defaults if untouched. */
    fun entriesFor(planExerciseId: String): ExerciseEntries = entries[planExerciseId] ?: ExerciseEntries()
}
