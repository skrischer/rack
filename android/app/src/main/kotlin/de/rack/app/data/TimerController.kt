package de.rack.app.data

import android.os.SystemClock
import de.rack.app.domain.RestTimer
import de.rack.app.domain.SessionTimer
import de.rack.app.ui.timer.RestUiState
import de.rack.app.ui.timer.SessionUiState
import de.rack.app.ui.timer.TimerUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * The process-wide host of the Phase-8 rest countdown and count-up session timer
 * (see docs/specs/spec-timers.md "Service lifecycle"). The engine state and its
 * drift-free tick loop live here — not in a ViewModel — so the foreground
 * [de.rack.app.timer.TimerService] can keep the timers running across
 * backgrounding/Doze and the [de.rack.app.ui.timer.TimerViewModel] is a thin
 * facade over the same single source of truth. All math is delegated to the pure
 * [de.rack.app.domain.TimerEngine] models, anchored on a monotonic elapsed-realtime
 * [clock] so the next read after a resume reflects real time, never accumulated
 * ticks.
 *
 * Lifecycle contract: the session starts on [startSession] (the first logged set)
 * and stops only on the explicit [stopSession]; a rest reaching zero or being
 * [skipRest]-ped never stops the session. [isSessionActive] reflects exactly that
 * window, and the service binds its own lifetime to it. [restFinished] emits once
 * per rest that crosses its end-instant so the service (and the in-app surface)
 * can fire the completion alert.
 */
class TimerController(
    private val clock: () -> Long = SystemClock::elapsedRealtime,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob()),
) {
    private val _uiState = MutableStateFlow(TimerUiState())
    val uiState: StateFlow<TimerUiState> = _uiState.asStateFlow()

    private val _isSessionActive = MutableStateFlow(false)

    /** `true` between [startSession] and [stopSession]; the service binds to this. */
    val isSessionActive: StateFlow<Boolean> = _isSessionActive.asStateFlow()

    private val _restFinished = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Emits once each time a running rest crosses zero, driving the completion alert. */
    val restFinished: SharedFlow<Unit> = _restFinished.asSharedFlow()

    private var restTimer: RestTimer? = null
    private var restDurationSeconds: Int = 0
    private var restAlerted: Boolean = false
    private var sessionTimer: SessionTimer? = null
    private var ticker: Job? = null

    /** Begin the count-up session at the current clock instant, if not already running. */
    fun startSession() {
        if (sessionTimer != null) return
        sessionTimer = SessionTimer.start(clock())
        _isSessionActive.value = true
        refresh()
        ensureTicking()
    }

    /** Explicitly end the session: the sole trigger that stops the session timer. */
    fun stopSession() {
        sessionTimer = null
        restTimer = null
        restDurationSeconds = 0
        restAlerted = false
        ticker?.cancel()
        ticker = null
        _isSessionActive.value = false
        _uiState.value = TimerUiState()
    }

    /** Start (or replace) the rest countdown with [durationSeconds] from now. */
    fun startRest(durationSeconds: Int) {
        restDurationSeconds = durationSeconds.coerceAtLeast(0)
        restTimer = RestTimer.start(restDurationSeconds, clock())
        restAlerted = false
        refresh()
        ensureTicking()
    }

    /** Add 15 s to the running rest; no-op when no rest is active. */
    fun addRest() = mutateRest { it.addStep() }

    /** Subtract 15 s from the running rest, clamped so remaining never goes negative. */
    fun subtractRest() = mutateRest { it.subtractStep(clock()) }

    /** Skip the rest (end it now); the session timer keeps running and no alert fires. */
    fun skipRest() {
        restAlerted = true
        mutateRest { it.skip(clock()) }
    }

    /** Restart the current rest at its original [restDurationSeconds] from now. */
    fun restartRest() {
        if (restTimer == null) return
        startRest(restDurationSeconds)
    }

    private fun mutateRest(transform: (RestTimer) -> RestTimer) {
        val current = restTimer ?: return
        restTimer = transform(current)
        refresh()
    }

    private fun ensureTicking() {
        if (ticker?.isActive == true) return
        ticker =
            scope.launch {
                while (isActive && (sessionTimer != null || restTimer != null)) {
                    refresh()
                    delay(TICK_INTERVAL_MS)
                }
            }
    }

    /** Recompute the exposed UI state from the anchored models and fire the alert once. */
    private fun refresh() {
        val now = clock()
        val rest = restTimer
        val session = sessionTimer
        if (rest != null && rest.isFinished(now) && !restAlerted) {
            restAlerted = true
            _restFinished.tryEmit(Unit)
        }
        _uiState.value =
            TimerUiState(
                rest = rest?.let { RestUiState(it.remainingSeconds(now), it.isFinished(now)) },
                session = session?.let { SessionUiState(it.elapsedSeconds(now)) },
            )
    }

    private companion object {
        const val TICK_INTERVAL_MS = 250L
    }
}
