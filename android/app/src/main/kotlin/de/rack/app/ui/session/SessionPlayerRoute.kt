package de.rack.app.ui.session

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.rack.app.di.AppContainer
import de.rack.app.di.appViewModelFactory
import de.rack.app.di.sessionPlayerViewModelFactory
import de.rack.app.ui.timer.RestCompletionVibration
import de.rack.app.ui.timer.TimerBar
import de.rack.app.ui.timer.TimerBarActions
import de.rack.app.ui.timer.TimerViewModel
import de.rack.app.ui.timer.rememberNotificationPermission

/**
 * Route entry point for the guided session player: resolves the per-[dayId]
 * [SessionPlayerViewModel] from the [container], collects its [SessionPlayerScreenState]
 * lifecycle-aware, and renders [SessionPlayerScreen], wiring the screen's edit / tick /
 * retry / confirm-save / abandon events to the ViewModel. The player's [closed] signal
 * (emitted after a confirmed save or an abandon) stops the session timer and pops via
 * [onClose] — the screen never closes directly, so an accidental back always routes
 * through the abandon confirmation.
 *
 * It also consumes the Phase-8 timer: the shared [TimerViewModel] starts the
 * session-duration timer on open and renders the existing [TimerBar] below the player;
 * the timer's elapsed seconds drive the summary duration. Each ticked set raises a
 * [RestPrompt] (classified type + group context) the route forwards to
 * [TimerViewModel.onSetTicked], which owns the type -> duration map and the countdown —
 * the player adds no timer logic. Kept in the session feature package so the central nav
 * host only references it.
 */
@Composable
fun SessionPlayerRoute(
    container: AppContainer,
    dayId: String,
    onClose: () -> Unit,
) {
    val factory = sessionPlayerViewModelFactory(container, dayId)
    val viewModel: SessionPlayerViewModel = viewModel(key = dayId, factory = factory)
    val timerViewModel: TimerViewModel = viewModel(factory = appViewModelFactory(container))
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val timerState by timerViewModel.uiState.collectAsStateWithLifecycle()
    val permission = rememberNotificationPermission()

    LaunchedEffect(Unit) { timerViewModel.startSession() }
    LaunchedEffect(viewModel) {
        viewModel.restPrompts.collect { prompt ->
            permission.request()
            timerViewModel.onSetTicked(type = prompt.type, context = prompt.context)
        }
    }
    LaunchedEffect(viewModel) {
        viewModel.closed.collect {
            timerViewModel.stopSession()
            onClose()
        }
    }
    RestCompletionVibration(restFinished = timerViewModel.restFinished)

    Column(modifier = Modifier.fillMaxSize()) {
        SessionPlayerScreen(
            state = uiState,
            durationSeconds = timerState.session?.elapsedSeconds ?: 0,
            actions =
                SessionPlayerActions(
                    onWeightChange = viewModel::onWeightChange,
                    onRirChange = viewModel::onRirChange,
                    onRepsChange = viewModel::onRepsChange,
                    onTick = viewModel::tickFocused,
                    onRetry = viewModel::load,
                    onConfirmSave = viewModel::confirmSave,
                    onAbandon = viewModel::abandon,
                ),
            modifier = Modifier.weight(1f),
        )
        TimerBar(
            state = timerState,
            notificationsDenied = !permission.isGranted,
            actions =
                TimerBarActions(
                    onAdd = timerViewModel::addRest,
                    onSubtract = timerViewModel::subtractRest,
                    onSkip = timerViewModel::skipRest,
                    onRestart = timerViewModel::restartRest,
                    onEndSession = timerViewModel::stopSession,
                ),
        )
    }
}
