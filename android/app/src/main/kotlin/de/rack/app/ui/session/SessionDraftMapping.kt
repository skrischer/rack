package de.rack.app.ui.session

import de.rack.app.data.SessionDraft
import de.rack.app.data.SessionDraftEntries
import de.rack.app.domain.PlanExercise

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
 * last-logged [references], and the cached [draft]: the step sequence is rebuilt
 * deterministically and split at the draft's `doneCount` cursor so the same step is
 * refocused, while the draft's entries replace the fresh pre-fills for any exercise the
 * user already edited. Untouched exercises keep their [prefilled] defaults. A draft saved
 * at the last cursor restores a finished session with its summary so the user resumes
 * straight on the confirm-save screen rather than a blank step.
 */
fun restoreSession(
    exercises: List<PlanExercise>,
    prefilled: Map<String, ExerciseEntries>,
    references: Map<String, String>,
    draft: SessionDraft,
): SessionPlayerUiState {
    val steps = buildSessionSteps(exercises)
    val cursor = draft.doneCount.coerceIn(0, steps.size)
    val done = steps.take(cursor)
    val entries = prefilled + draft.entries.toExerciseEntries()
    return SessionPlayerUiState(
        focused = steps.getOrNull(cursor),
        remaining = if (cursor < steps.size) steps.drop(cursor + 1) else emptyList(),
        done = done,
        entries = entries,
        references = references,
        summary = if (cursor >= steps.size) buildSessionSummary(exercises, done, entries) else null,
    )
}
