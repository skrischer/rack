package de.rack.app.ui.session

import de.rack.app.data.SessionDraft
import de.rack.app.data.SessionDraftEntries
import de.rack.app.domain.PlanExercise
import de.rack.app.domain.WeightUnit

/*
 * Maps between the session player's UI working state ([ExerciseEntries], the stepped
 * session) and the cache's draft model ([SessionDraft]) so the player resumes the same
 * step with ticked sets, per-exercise kg/RIR, and per-set reps intact after backgrounding
 * or process death (docs/specs/spec-session-player.md). Pure functions; the repository
 * owns serialization, the ViewModel owns the running session.
 */

/** The per-exercise [ExerciseEntries] as the cache's serializable [SessionDraftEntries]. */
fun Map<String, ExerciseEntries>.toDraftEntries(): Map<String, SessionDraftEntries> =
    mapValues { (_, entries) -> SessionDraftEntries(entries.weight, entries.rir, entries.reps) }

/** The cache's [SessionDraftEntries] back into the UI [ExerciseEntries]. */
private fun Map<String, SessionDraftEntries>.toExerciseEntries(): Map<String, ExerciseEntries> =
    mapValues { (_, entries) -> ExerciseEntries(entries.weight, entries.rir, entries.reps) }

/**
 * Rebuilds the running [SessionPlayerUiState] from the day's [exercises], the per-exercise
 * last-logged [references], and the cached [draft]: the deterministic step sequence is
 * rebuilt and its first `doneCount` sets are restored as ticked, while the draft's entries
 * replace the fresh pre-fills for any exercise the user already edited. Untouched exercises
 * keep their [prefilled] defaults. A draft saved with every set ticked restores a finished
 * session with its summary so the user resumes on the confirm-save screen. The caller
 * augments the result with the static blocks and per-set "Vorher" strings. Sets ticked out
 * of order resume in set order (the draft caches only the ticked count, not which sets).
 */
fun restoreSession(
    exercises: List<PlanExercise>,
    prefilled: Map<String, ExerciseEntries>,
    references: Map<String, String>,
    draft: SessionDraft,
    unit: WeightUnit,
): SessionPlayerUiState {
    val steps = buildSessionSteps(exercises)
    val cursor = draft.doneCount.coerceIn(0, steps.size)
    val done = steps.take(cursor)
    val entries = prefilled + draft.entries.toExerciseEntries()
    val finished = cursor >= steps.size && steps.isNotEmpty()
    return SessionPlayerUiState(
        done = done,
        entries = entries,
        references = references,
        finished = finished,
        summary = if (finished) buildSessionSummary(exercises, done, entries, unit) else null,
        weightUnit = unit,
    )
}
