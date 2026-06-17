package de.rack.app.ui.timer

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import de.rack.app.data.TimerController
import de.rack.app.domain.resolveGroupRotation
import de.rack.app.domain.resolveRestSeconds
import de.rack.app.ui.plan.LoggedExerciseContext
import de.rack.app.ui.theme.SupersetKind
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The rest + session controls the in-app [TimerBar] forwards to the [TimerViewModel],
 * bundled so the bar takes one parameter. Each mirrors a notification action so both
 * surfaces behave identically (docs/specs/spec-timers.md).
 */
@Immutable
data class TimerBarActions(
    val onAdd: () -> Unit,
    val onSubtract: () -> Unit,
    val onSkip: () -> Unit,
    val onRestart: () -> Unit,
    val onEndSession: () -> Unit,
)

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

    /**
     * Auto-start the timers on the existing Phase-3 log-set action: begin the session
     * (and its foreground service) on the very first logged set, then start the rest
     * countdown at the exercise-type duration and record the superset/circuit rotation
     * cue, all resolved from the logged exercise's [context] (its catalog [category]
     * and group). See docs/specs/spec-timers.md. A rest reaching zero or being skipped
     * never stops the session; only [stopSession] does.
     */
    fun onSetLogged(
        category: String?,
        context: LoggedExerciseContext,
    ) {
        if (!controller.isSessionActive.value) startSession()
        val seconds = resolveRestSeconds(category, context.group.size)
        controller.startRest(seconds, rotationFor(context))
    }

    /** Build the in-app rotation cue from [context], or null for a standalone exercise. */
    private fun rotationFor(context: LoggedExerciseContext): RotationUiState? {
        val rotation = resolveGroupRotation(context.group, context.index)
        val next = rotation.next.takeIf { rotation.role != SupersetKind.NONE } ?: return null
        return RotationUiState(kind = rotation.role, nextExerciseName = next.name)
    }

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
