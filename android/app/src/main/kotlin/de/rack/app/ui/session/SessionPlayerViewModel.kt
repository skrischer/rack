package de.rack.app.ui.session

import androidx.lifecycle.ViewModel
import de.rack.app.domain.PlanExercise
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Models one plan day as a stepped guided session: it turns the day's
 * position-ordered [exercises] into the ordered step sequence (with
 * set-synchronized superset/circuit rotation) and holds the working entries
 * (per-set reps plus one per-exercise kg and one per-exercise RIR) as the user
 * walks through it. It exposes that as [uiState] [StateFlow]; the player screen
 * (#58) renders the focused step and emits edit/tick events — no stepping,
 * rotation, or business logic lives in Composables. Persistence to `set_logs` on
 * confirm is a later step (#60); this model is in-memory only.
 */
class SessionPlayerViewModel(
    exercises: List<PlanExercise>,
) : ViewModel() {
    private val _uiState = MutableStateFlow(initialState(exercises))
    val uiState: StateFlow<SessionPlayerUiState> = _uiState.asStateFlow()

    /** Edit the focused exercise's single per-exercise kg input. */
    fun onWeightChange(value: String) = editFocusedEntries { it.copy(weight = value) }

    /** Edit the focused exercise's single per-exercise RIR input. */
    fun onRirChange(value: String) = editFocusedEntries { it.copy(rir = value) }

    /** Edit the reps entered for the focused set of the focused exercise. */
    fun onRepsChange(value: String) =
        _uiState.update { state ->
            val step = state.focused ?: return@update state
            state.editEntries(step.planExerciseId) { it.withReps(step.setIndex, value) }
        }

    /**
     * Tick the focused set: move it from [SessionPlayerUiState.remaining] into
     * [SessionPlayerUiState.done] and focus the next remaining step. The set's reps
     * and the exercise's kg/RIR are already captured in the entries by the edit
     * handlers; ticking only advances the cursor. On the last step [focused]
     * becomes null and the session is finished.
     */
    fun tickFocused() =
        _uiState.update { state ->
            val current = state.focused ?: return@update state
            state.copy(
                focused = state.remaining.firstOrNull(),
                remaining = state.remaining.drop(1),
                done = state.done + current,
            )
        }

    private fun editFocusedEntries(transform: (ExerciseEntries) -> ExerciseEntries) =
        _uiState.update { state ->
            val step = state.focused ?: return@update state
            state.editEntries(step.planExerciseId, transform)
        }

    private companion object {
        fun initialState(exercises: List<PlanExercise>): SessionPlayerUiState {
            val steps = buildSessionSteps(exercises)
            return SessionPlayerUiState(
                focused = steps.firstOrNull(),
                remaining = steps.drop(1),
            )
        }
    }
}

private fun SessionPlayerUiState.editEntries(
    planExerciseId: String,
    transform: (ExerciseEntries) -> ExerciseEntries,
): SessionPlayerUiState = copy(entries = entries + (planExerciseId to transform(entriesFor(planExerciseId))))
