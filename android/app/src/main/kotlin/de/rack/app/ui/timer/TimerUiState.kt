package de.rack.app.ui.timer

/**
 * The timer state the log screen observes (see docs/specs/spec-timers.md). Both
 * fields are derived from the anchored engine models on each tick by
 * [TimerViewModel]; the UI only renders them. A null field means that timer is not
 * currently active.
 */
data class TimerUiState(
    val rest: RestUiState? = null,
    val session: SessionUiState? = null,
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
