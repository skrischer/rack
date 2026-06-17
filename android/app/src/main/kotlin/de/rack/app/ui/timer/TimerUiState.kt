package de.rack.app.ui.timer

import de.rack.app.ui.theme.SupersetKind

/**
 * The timer state the log screen observes (see docs/specs/spec-timers.md). The
 * [rest] and [session] fields are derived from the anchored engine models on each
 * tick by the controller; [rotation] is the superset/circuit cue recorded with the
 * current rest. The UI only renders these; a null field means that surface is not
 * currently active. [rotation] is in-app only — the notification ignores it.
 */
data class TimerUiState(
    val rest: RestUiState? = null,
    val session: SessionUiState? = null,
    val rotation: RotationUiState? = null,
)

/** The running rest countdown: whole seconds [remainingSeconds] left and whether it [finished]. */
data class RestUiState(
    val remainingSeconds: Int,
    val finished: Boolean,
)

/** The running count-up session timer: whole [elapsedSeconds] since it started. */
data class SessionUiState(
    val elapsedSeconds: Int,
)

/**
 * The superset/circuit rotation cue shown alongside the rest bar after a grouped
 * set is logged: the group [kind] (superset vs circuit) and the [nextExerciseName]
 * to perform before the next rest. Only present for a grouped exercise; a standalone
 * exercise records no rotation.
 */
data class RotationUiState(
    val kind: SupersetKind,
    val nextExerciseName: String,
)
