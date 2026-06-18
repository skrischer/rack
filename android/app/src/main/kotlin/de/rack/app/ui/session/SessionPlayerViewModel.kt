package de.rack.app.ui.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.rack.app.data.LoggingRepository
import de.rack.app.data.SessionDraftRepository
import de.rack.app.data.SettingsRepository
import de.rack.app.data.TrainingRepository
import de.rack.app.domain.PlanExercise
import de.rack.app.domain.SetLog
import de.rack.app.domain.WeightUnit
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
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
 *
 * It exposes that as [uiState] [StateFlow]; the player screen renders the focused step
 * and emits edit/tick events — no stepping, rotation, or aggregation lives in
 * Composables. On finishing the last step it aggregates the [SessionSummary]
 * (per-exercise sets-done/volume); [confirmSave] persists every logged exercise to
 * `set_logs` through the existing Phase-3 [LoggingRepository] path (one row per
 * `plan_exercise_id`, scalar weight, scalar rir, reps[] of its ticked sets,
 * `source='app'`, no `user_id` from the client), while [abandon] discards. The running
 * session is cached after each change through the [SessionDraftRepository] so a
 * backgrounded / killed app resumes the same step. See docs/specs/spec-session-player.md.
 */
class SessionPlayerViewModel(
    private val repository: TrainingRepository,
    private val logging: LoggingRepository,
    private val drafts: SessionDraftRepository,
    private val settings: SettingsRepository,
    private val dayId: String,
) : ViewModel() {
    // The unit the session displays/enters weights in, captured at load so prefills,
    // volume, and the save conversion stay consistent for the session's lifetime.
    private val unit: WeightUnit get() = settings.weightUnit.value
    private val _uiState = MutableStateFlow<SessionPlayerScreenState>(SessionPlayerScreenState.Loading)
    val uiState: StateFlow<SessionPlayerScreenState> = _uiState.asStateFlow()

    private val _restPrompts = MutableSharedFlow<RestPrompt>(extraBufferCapacity = 1)

    /**
     * Emits once per ticked set the resolved [RestPrompt] (classified exercise type +
     * group context) so the host wires it to the Phase-8 rest timer. The player owns the
     * classification, not the type -> duration map or the countdown (Phase 8 owns those).
     */
    val restPrompts: SharedFlow<RestPrompt> = _restPrompts.asSharedFlow()

    private val _closed = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Emits once when an abandoned or saved session should close, so the host pops back. */
    val closed: SharedFlow<Unit> = _closed.asSharedFlow()

    private var exercises: List<PlanExercise> = emptyList()
    private var steps: List<SessionStep> = emptyList()

    init {
        load()
    }

    /** (Re)read the day's exercises and last logs, restoring any cached draft; retried on error. */
    fun load() {
        _uiState.value = SessionPlayerScreenState.Loading
        viewModelScope.launch {
            runCatching { loadSession(repository, drafts, dayId, unit) }
                .onSuccess { (loaded, session) ->
                    exercises = loaded
                    steps = buildSessionSteps(loaded)
                    _uiState.value = SessionPlayerScreenState.Content(session)
                }
                .onFailure { error -> _uiState.value = SessionPlayerScreenState.Error(messageFor(error)) }
        }
    }

    /** Edit an exercise's single per-exercise kg input (scalar, applied to every set). */
    fun onWeightChange(
        planExerciseId: String,
        value: String,
    ) = updateSession { it.editEntries(planExerciseId) { entries -> entries.copy(weight = value) } }

    /** Edit an exercise's single per-exercise RIR input (scalar, applied to every set). */
    fun onRirChange(
        planExerciseId: String,
        value: String,
    ) = updateSession { it.editEntries(planExerciseId) { entries -> entries.copy(rir = value) } }

    /** Edit the reps entered for one set of an exercise. */
    fun onRepsChange(
        planExerciseId: String,
        setIndex: Int,
        value: String,
    ) = updateSession { it.editEntries(planExerciseId) { entries -> entries.withReps(setIndex, value) } }

    /**
     * Toggle a set's check. Ticking it on auto-prompts the rest timer for the exercise and
     * records the set in [SessionPlayerUiState.done] (the persistence happens at
     * confirm-save); tapping a ticked set again un-ticks it and raises no rest prompt. The
     * session ends only when the user explicitly [finish]es it.
     */
    fun tickSet(
        planExerciseId: String,
        setIndex: Int,
    ) {
        val session = (_uiState.value as? SessionPlayerScreenState.Content)?.session ?: return
        if (session.isSetDone(planExerciseId, setIndex)) {
            updateSession { it.copy(done = it.done.filterNot { step -> step.matches(planExerciseId, setIndex) }) }
        } else {
            steps.firstOrNull { it.matches(planExerciseId, setIndex) }?.let { step ->
                restPromptFor(exercises, planExerciseId)?.let(_restPrompts::tryEmit)
                updateSession { it.copy(done = it.done + step) }
            }
        }
    }

    /**
     * End the session: aggregate the [SessionSummary] (per-exercise sets-done/volume) from
     * the ticked sets and entries and flip to the confirm/abandon screen. Idempotent once
     * finished; the actual `set_logs` write happens in [confirmSave].
     */
    fun finish() =
        updateSession(persist = false) { state ->
            if (state.finished) {
                state
            } else {
                state.copy(finished = true, summary = buildSessionSummary(exercises, state.done, state.entries, unit))
            }
        }

    /**
     * Confirm the summary: write one `set_logs` row per logged `plan_exercise_id`
     * through the existing [LoggingRepository] (scalar weight, scalar rir, reps[] of the
     * ticked sets, `source='app'`); on success clear the cached draft and close. The
     * client sends no `user_id` — [LoggingRepository.buildLog] resolves it from the JWT.
     */
    fun confirmSave() {
        val session = (_uiState.value as? SessionPlayerScreenState.Content)?.session ?: return
        if (!session.isFinished || session.summary?.isEmpty != false) return
        setSave(SessionSaveState.SAVING)
        viewModelScope.launch {
            runCatching { logging.saveSession(session.loggedInputs(unit)) }
                .onSuccess {
                    drafts.clear(dayId)
                    setSave(SessionSaveState.SAVED)
                    _closed.tryEmit(Unit)
                }
                .onFailure { setSave(SessionSaveState.ERROR) }
        }
    }

    /** Abandon the session: discard the cached draft, write nothing, and close. */
    fun abandon() {
        viewModelScope.launch {
            drafts.clear(dayId)
            _closed.tryEmit(Unit)
        }
    }

    private fun setSave(state: SessionSaveState) = updateSession(persist = false) { it.copy(saveState = state) }

    private fun updateSession(
        persist: Boolean = true,
        transform: (SessionPlayerUiState) -> SessionPlayerUiState,
    ) {
        var next: SessionPlayerUiState? = null
        _uiState.update { current ->
            val content = current as? SessionPlayerScreenState.Content ?: return@update current
            content.copy(session = transform(content.session).also { next = it })
        }
        if (!persist) return
        next?.let { s -> viewModelScope.launch { drafts.save(dayId, s.done.size, s.entries.toDraftEntries()) } }
    }
}

private const val GENERIC_ERROR = "Could not start the session. Check your connection and try again."

/**
 * Reads the day's position-ordered exercises and each one's last logged set, then builds
 * the set-table session: a cached draft for [dayId] resumes the ticked sets and entries,
 * otherwise a fresh session starts with target / last-logged pre-fills. The per-exercise
 * blocks (card order + group headers) and per-set "Vorher" strings are computed once and
 * carried on the state. Returns the exercises (retained for rest-prompt classification and
 * summary aggregation) and the initial UI state.
 */
private suspend fun loadSession(
    repository: TrainingRepository,
    drafts: SessionDraftRepository,
    dayId: String,
    unit: WeightUnit,
): Pair<List<PlanExercise>, SessionPlayerUiState> {
    val exercises = repository.getPlanExercises(dayId)
    val lastLogs = lastLogsFor(repository, exercises)
    val prefilled = prefillEntries(exercises, lastLogs, unit)
    val references = lastLogs.mapValues { (_, log) -> referenceLine(log, unit) }
    val previous = previousSets(exercises, lastLogs, unit)
    val blocks = buildExerciseBlocks(exercises)
    val draft = drafts.load(dayId)
    val base =
        if (draft != null) {
            restoreSession(exercises, prefilled, references, draft, unit)
        } else {
            SessionPlayerUiState(entries = prefilled, references = references, weightUnit = unit)
        }
    return exercises to base.copy(blocks = blocks, previous = previous)
}

private fun SessionStep.matches(
    planExerciseId: String,
    setIndex: Int,
): Boolean = this.planExerciseId == planExerciseId && this.setIndex == setIndex

private fun SessionPlayerUiState.editEntries(
    planExerciseId: String,
    transform: (ExerciseEntries) -> ExerciseEntries,
): SessionPlayerUiState = copy(entries = entries + (planExerciseId to transform(entriesFor(planExerciseId))))

private suspend fun lastLogsFor(
    repository: TrainingRepository,
    exercises: List<PlanExercise>,
): Map<String, SetLog> =
    exercises.mapNotNull { exercise ->
        repository.getSetLogs(exercise.id).firstOrNull()?.let { exercise.id to it }
    }.toMap()

private fun messageFor(error: Throwable): String = error.message?.takeIf { it.isNotBlank() } ?: GENERIC_ERROR
