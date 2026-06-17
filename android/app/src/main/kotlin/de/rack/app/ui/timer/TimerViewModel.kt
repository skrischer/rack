package de.rack.app.ui.timer

import androidx.lifecycle.ViewModel
import de.rack.app.data.TimerController
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Thin facade over the process-wide [TimerController] for the log screen (no timer
 * logic lives in Composables; see docs/specs/spec-timers.md). The engine state and
 * its drift-free tick loop live in the controller — shared with the foreground
 * [de.rack.app.timer.TimerService] — so this ViewModel only re-exposes the state
 * [StateFlow] and routes the user controls (+15 s / -15 s / skip / restart).
 *
 * Lifecycle contract (spec "Service lifecycle"): [startSession] (the first logged
 * set) starts the session and the foreground service via [onSessionStart];
 * [stopSession] is the sole trigger that ends them via [onSessionStop]. A rest
 * reaching zero or being [skipRest]-ped never stops the session. [onSessionStart]
 * and [onSessionStop] default to no-ops so the math stays unit testable without
 * Android; the host wires them to start/stop the service.
 */
class TimerViewModel(
    private val controller: TimerController,
    private val onSessionStart: () -> Unit = {},
    private val onSessionStop: () -> Unit = {},
) : ViewModel() {
    val uiState: StateFlow<TimerUiState> = controller.uiState

    /** Emits once each time a running rest crosses zero, for the in-app completion alert. */
    val restFinished: SharedFlow<Unit> = controller.restFinished

    /** Begin the count-up session and start the foreground service hosting it. */
    fun startSession() {
        controller.startSession()
        onSessionStart()
    }

    /** Explicitly end the session: stops the timers and the foreground service. */
    fun stopSession() {
        controller.stopSession()
        onSessionStop()
    }

    /** Start (or replace) the rest countdown with [durationSeconds] from now. */
    fun startRest(durationSeconds: Int) = controller.startRest(durationSeconds)

    /** Add 15 s to the running rest; no-op when no rest is active. */
    fun addRest() = controller.addRest()

    /** Subtract 15 s from the running rest, clamped so remaining never goes negative. */
    fun subtractRest() = controller.subtractRest()

    /** Skip the rest (end it now); the session timer keeps running. */
    fun skipRest() = controller.skipRest()

    /** Restart the current rest at its original duration from now. */
    fun restartRest() = controller.restartRest()
}
