package de.rack.app.ui.session

import androidx.compose.runtime.Immutable

/**
 * The session-player actions, bundled so the screen takes one parameter instead of
 * separate lambdas: edit the focused exercise's single kg / RIR, edit the focused
 * set's reps, tick the focused set, retry a failed load, confirm-save the finished
 * session to `set_logs`, and abandon it (discard, no write). Each maps to a
 * [SessionPlayerViewModel] method; the Composable holds no logic. Closing the player
 * routes through the abandon confirmation, so there is no direct close action.
 */
@Immutable
data class SessionPlayerActions(
    val onWeightChange: (String) -> Unit,
    val onRirChange: (String) -> Unit,
    val onRepsChange: (String) -> Unit,
    val onTick: () -> Unit,
    val onRetry: () -> Unit,
    val onConfirmSave: () -> Unit,
    val onAbandon: () -> Unit,
)
