package de.rack.app.ui.timer

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.rack.app.domain.RestTimer
import de.rack.app.domain.SessionTimer
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Drives the Phase-8 rest countdown and count-up session timer and exposes their
 * state as a [StateFlow] for the log screen (no timer logic lives in Composables;
 * see docs/specs/spec-timers.md). All math is delegated to the pure
 * [de.rack.app.domain.TimerEngine] models, anchored on a monotonic elapsed-realtime
 * [clock] so backgrounding never drifts; this ViewModel only re-reads them on a
 * tick and routes the user controls (+15 s / -15 s / skip / restart).
 *
 * Lifecycle contract (spec "Service lifecycle"): the session timer starts on
 * [startSession] (the first logged set) and stops only on the explicit
 * [stopSession]. A rest reaching zero or being [skipRest]-ped never stops the
 * session — [TimerUiState.session] stays running until [stopSession] is called.
 * The [clock] is injectable so the engine math is unit testable without Android.
 */
class TimerViewModel(
    private val clock: () -> Long = SystemClock::elapsedRealtime,
) : ViewModel() {
    private val _uiState = MutableStateFlow(TimerUiState())
    val uiState: StateFlow<TimerUiState> = _uiState.asStateFlow()

    private var restTimer: RestTimer? = null
    private var restDurationSeconds: Int = 0
    private var sessionTimer: SessionTimer? = null
    private var ticker: Job? = null

    /** Begin the count-up session at the current clock instant, if not already running. */
    fun startSession() {
        if (sessionTimer != null) return
        sessionTimer = SessionTimer.start(clock())
        refresh()
        ensureTicking()
    }

    /** Explicitly end the session: the sole trigger that stops the session timer. */
    fun stopSession() {
        sessionTimer = null
        restTimer = null
        restDurationSeconds = 0
        ticker?.cancel()
        ticker = null
        _uiState.value = TimerUiState()
    }

    /** Start (or replace) the rest countdown with [durationSeconds] from now. */
    fun startRest(durationSeconds: Int) {
        restDurationSeconds = durationSeconds.coerceAtLeast(0)
        restTimer = RestTimer.start(restDurationSeconds, clock())
        refresh()
        ensureTicking()
    }

    /** Add 15 s to the running rest; no-op when no rest is active. */
    fun addRest() = mutateRest { it.addStep() }

    /** Subtract 15 s from the running rest, clamped so remaining never goes negative. */
    fun subtractRest() = mutateRest { it.subtractStep(clock()) }

    /** Skip the rest (end it now); the session timer keeps running. */
    fun skipRest() = mutateRest { it.skip(clock()) }

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
            viewModelScope.launch {
                while (isActive && (sessionTimer != null || restTimer != null)) {
                    refresh()
                    delay(TICK_INTERVAL_MS)
                }
            }
    }

    /** Recompute the exposed UI state from the anchored models against the clock. */
    private fun refresh() {
        val now = clock()
        val rest = restTimer
        val session = sessionTimer
        _uiState.update {
            TimerUiState(
                rest = rest?.let { RestUiState(it.remainingSeconds(now), it.isFinished(now)) },
                session = session?.let { SessionUiState(it.elapsedSeconds(now)) },
            )
        }
    }

    private companion object {
        const val TICK_INTERVAL_MS = 250L
    }
}
