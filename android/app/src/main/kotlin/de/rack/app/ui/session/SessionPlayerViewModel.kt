package de.rack.app.ui.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.rack.app.data.TrainingRepository
import de.rack.app.domain.PlanExercise
import de.rack.app.domain.SetLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The session player's load-state wrapper: loading, the running session, or an error. */
sealed interface SessionPlayerScreenState {
    data object Loading : SessionPlayerScreenState

    data class Content(val session: SessionPlayerUiState) : SessionPlayerScreenState

    data class Error(val message: String) : SessionPlayerScreenState
}

/**
 * Models one plan day as a stepped guided session: it reads the day's
 * position-ordered `plan_exercises` and each exercise's last logged set through the
 * [TrainingRepository], turns them into the ordered step sequence (with
 * set-synchronized superset/circuit rotation), pre-fills the working entries (per-set
 * reps plus one per-exercise kg and one per-exercise RIR, matching the scalar
 * weight/rir + reps[] shape of set_logs), and holds them as the user walks through it.
 * It exposes that as [uiState] [StateFlow]; the player screen renders the focused step
 * and emits edit/tick events — no stepping, rotation, or business logic lives in
 * Composables. Persistence to `set_logs` on confirm is a later step (#60); the running
 * session is in-memory only. See docs/specs/spec-session-player.md.
 */
class SessionPlayerViewModel(
    private val repository: TrainingRepository,
    private val dayId: String,
) : ViewModel() {
    private val _uiState = MutableStateFlow<SessionPlayerScreenState>(SessionPlayerScreenState.Loading)
    val uiState: StateFlow<SessionPlayerScreenState> = _uiState.asStateFlow()

    init {
        load()
    }

    /** (Re)read the day's exercises and last logs and build the session; retried on error. */
    fun load() {
        _uiState.value = SessionPlayerScreenState.Loading
        viewModelScope.launch {
            runCatching { buildSession() }
                .onSuccess { session -> _uiState.value = SessionPlayerScreenState.Content(session) }
                .onFailure { error -> _uiState.value = SessionPlayerScreenState.Error(messageFor(error)) }
        }
    }

    /** Edit the focused exercise's single per-exercise kg input. */
    fun onWeightChange(value: String) = editFocusedEntries { it.copy(weight = value) }

    /** Edit the focused exercise's single per-exercise RIR input. */
    fun onRirChange(value: String) = editFocusedEntries { it.copy(rir = value) }

    /** Edit the reps entered for the focused set of the focused exercise. */
    fun onRepsChange(value: String) =
        updateSession { state ->
            val step = state.focused ?: return@updateSession state
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
        updateSession { state ->
            val current = state.focused ?: return@updateSession state
            state.copy(
                focused = state.remaining.firstOrNull(),
                remaining = state.remaining.drop(1),
                done = state.done + current,
            )
        }

    private suspend fun buildSession(): SessionPlayerUiState {
        val exercises = repository.getPlanExercises(dayId)
        val lastLogs = lastLogsFor(exercises)
        val steps = buildSessionSteps(exercises)
        return SessionPlayerUiState(
            focused = steps.firstOrNull(),
            remaining = steps.drop(1),
            entries = prefillEntries(exercises, lastLogs),
            references = lastLogs.mapValues { (_, log) -> referenceLine(log) },
        )
    }

    private suspend fun lastLogsFor(exercises: List<PlanExercise>): Map<String, SetLog> =
        exercises.mapNotNull { exercise ->
            repository.getSetLogs(exercise.id).firstOrNull()?.let { exercise.id to it }
        }.toMap()

    private fun editFocusedEntries(transform: (ExerciseEntries) -> ExerciseEntries) =
        updateSession { state ->
            val step = state.focused ?: return@updateSession state
            state.editEntries(step.planExerciseId, transform)
        }

    private fun updateSession(transform: (SessionPlayerUiState) -> SessionPlayerUiState) =
        _uiState.update { current ->
            val content = current as? SessionPlayerScreenState.Content ?: return@update current
            content.copy(session = transform(content.session))
        }

    private fun messageFor(error: Throwable): String = error.message?.takeIf { it.isNotBlank() } ?: GENERIC_ERROR

    private companion object {
        const val GENERIC_ERROR = "Could not start the session. Check your connection and try again."
    }
}

private fun SessionPlayerUiState.editEntries(
    planExerciseId: String,
    transform: (ExerciseEntries) -> ExerciseEntries,
): SessionPlayerUiState = copy(entries = entries + (planExerciseId to transform(entriesFor(planExerciseId))))
