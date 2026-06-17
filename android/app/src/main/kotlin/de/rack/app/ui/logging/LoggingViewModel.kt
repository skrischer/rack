package de.rack.app.ui.logging

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.rack.app.data.ConnectivityObserver
import de.rack.app.data.LoggingRepository
import de.rack.app.data.RealtimeRepository
import de.rack.app.data.TrainingRepository
import de.rack.app.domain.HighlightTracker
import de.rack.app.domain.SetLog
import de.rack.app.domain.SetLogChange
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Holds the per-exercise logging state for the plan screen. It loads each
 * exercise's history from Supabase, edits the kg/per-set-reps inputs, and logs a
 * set: an optimistic in-memory history insert followed by an idempotent write
 * through the [LoggingRepository], reconciled with the persisted row. Unsynced
 * logs are flushed on reconnect (ConnectivityManager) and on foreground/login.
 *
 * It also reconciles the live [RealtimeRepository.setLogChanges] stream into the
 * same history by primary key (last-write-wins): the app's own `source='app'`
 * echo updates the row in place without duplicating it or highlighting it, while
 * an agent edit (`source='agent'`) flags the row for the transient highlight #28
 * renders. No Supabase access or business logic lives in Composables — the screen
 * observes [uiState] and emits events only.
 */
class LoggingViewModel(
    private val training: TrainingRepository,
    private val logging: LoggingRepository,
    realtime: RealtimeRepository,
    connectivity: ConnectivityObserver,
) : ViewModel() {
    private val _uiState = MutableStateFlow(LoggingUiState())
    val uiState: StateFlow<LoggingUiState> = _uiState.asStateFlow()

    private val highlights = HighlightTracker(viewModelScope)

    init {
        viewModelScope.launch {
            connectivity.onAvailable().collect { flushPending() }
        }
        viewModelScope.launch {
            realtime.setLogChanges().collect(::reconcileRealtime)
        }
        viewModelScope.launch {
            highlights.highlighted.collect { ids ->
                _uiState.update { current ->
                    current.copy(byExercise = current.byExercise.mapValues { (_, s) -> s.withHighlighted(ids) })
                }
            }
        }
        flushPending()
    }

    /** Ensure [planExerciseId] has a state row (with its [setCount]) and its history. */
    fun prepare(
        planExerciseId: String,
        setCount: Int,
    ) {
        if (_uiState.value.byExercise.containsKey(planExerciseId)) return
        update(planExerciseId) { it ?: ExerciseLogState(setCount = setCount) }
        loadHistory(planExerciseId)
    }

    fun onWeightChange(
        planExerciseId: String,
        value: String,
    ) = update(planExerciseId) { it?.copy(weightInput = value) }

    fun onRepChange(
        planExerciseId: String,
        index: Int,
        value: String,
    ) = update(planExerciseId) { state ->
        state?.copy(repsInputs = state.repsInputs.replaceAt(index, value))
    }

    fun toggleHistory(planExerciseId: String) =
        update(planExerciseId) { it?.copy(historyExpanded = it.historyExpanded.not()) }

    /** Log the current inputs for [planExerciseId]: optimistic insert then write. */
    fun log(planExerciseId: String) {
        val state = _uiState.value.byExercise[planExerciseId]
        val pending = state?.takeIf { it.canLog }?.let { it.buildPendingFor(planExerciseId) } ?: return
        update(planExerciseId) { it?.withOptimistic(pending.toSetLog()) }
        viewModelScope.launch {
            runCatching { logging.log(pending) }
                .onSuccess { persisted -> update(planExerciseId) { it?.reconciled(pending.id, persisted) } }
                .onFailure { error -> update(planExerciseId) { it?.failed(error) } }
        }
    }

    private fun ExerciseLogState.buildPendingFor(planExerciseId: String) =
        this@LoggingViewModel.logging.buildLog(
            planExerciseId = planExerciseId,
            weight = weightInput.trim().toDoubleOrNull(),
            reps = repsInputs.mapNotNull { it.trim().toIntOrNull() },
            rir = null,
        )

    private fun loadHistory(planExerciseId: String) {
        viewModelScope.launch {
            runCatching { training.getSetLogs(planExerciseId) }
                .onSuccess { logs ->
                    update(planExerciseId) { it?.copy(history = mergeHistory(it.history, logs)) }
                }
        }
    }

    private fun flushPending() {
        viewModelScope.launch {
            logging.flushPending().forEach { persisted ->
                update(persisted.planExerciseId) { it?.copy(history = it.replaceEntry(persisted.id, persisted)) }
            }
        }
    }

    /**
     * Apply a live set-log [change] to the exercise it belongs to and update its
     * transient highlight: an agent edit flags the row (glow + 3 s fade), the app's
     * own `source='app'` echo clears any flag so a self-edit is never highlighted.
     * Reconciliation runs only for an exercise already on screen ([update] skips an
     * absent key); an unprepared exercise picks the change up from [prepare]'s read.
     */
    private fun reconcileRealtime(change: SetLogChange) {
        update(change.log.planExerciseId) { it?.applyRealtimeChange(change) }
        if (change.isAgentEdit) highlights.flag(change.log.id) else highlights.clear(change.log.id)
    }

    private fun update(
        planExerciseId: String,
        transform: (ExerciseLogState?) -> ExerciseLogState?,
    ) {
        _uiState.update { current ->
            val next = transform(current.byExercise[planExerciseId]) ?: return@update current
            current.copy(byExercise = current.byExercise + (planExerciseId to next))
        }
    }
}

private fun List<String>.replaceAt(
    index: Int,
    value: String,
): List<String> = mapIndexed { i, existing -> if (i == index) value else existing }

/** Merge server history with any optimistic/cached entries already present, server first. */
private fun mergeHistory(
    existing: List<SetLog>,
    server: List<SetLog>,
): List<SetLog> {
    val serverIds = server.map(SetLog::id).toSet()
    val pendingOnly = existing.filter { it.id !in serverIds }
    return pendingOnly + server
}
