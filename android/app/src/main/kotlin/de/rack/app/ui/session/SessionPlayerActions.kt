package de.rack.app.ui.session

import androidx.compose.runtime.Immutable

/**
 * The session-player set-table actions, bundled so the screen takes one parameter instead
 * of separate lambdas: edit an exercise's single kg / RIR (scalar per `set_logs` row),
 * edit one set's reps, toggle a set's check (which persists it and auto-starts the rest
 * timer), end the session into the confirm-save summary, retry a failed load, confirm-save
 * to `set_logs`, and abandon it (discard, no write). Each maps to a
 * [SessionPlayerViewModel] method; the Composable holds no logic. Closing the player
 * routes through the abandon confirmation, so there is no direct close action.
 */
@Immutable
data class SessionPlayerActions(
    val onWeightChange: (planExerciseId: String, value: String) -> Unit,
    val onRepsChange: (planExerciseId: String, setIndex: Int, value: String) -> Unit,
    val onRirChange: (planExerciseId: String, value: String) -> Unit,
    val onToggleSet: (planExerciseId: String, setIndex: Int) -> Unit,
    val onFinish: () -> Unit,
    val onRetry: () -> Unit,
    val onConfirmSave: () -> Unit,
    val onAbandon: () -> Unit,
)
