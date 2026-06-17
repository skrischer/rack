package de.rack.app.ui.session

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.rack.app.di.AppContainer
import de.rack.app.di.sessionPlayerViewModelFactory

/**
 * Route entry point for the guided session player: resolves the per-[dayId]
 * [SessionPlayerViewModel] from the [container], collects its [SessionPlayerScreenState]
 * lifecycle-aware, and renders [SessionPlayerScreen], wiring the screen's edit / tick /
 * retry events to the ViewModel and [onClose] to the caller's back navigation. Kept in
 * the session feature package so the central nav host only references it.
 */
@Composable
fun SessionPlayerRoute(
    container: AppContainer,
    dayId: String,
    onClose: () -> Unit,
) {
    val factory = sessionPlayerViewModelFactory(container, dayId)
    val viewModel: SessionPlayerViewModel = viewModel(key = dayId, factory = factory)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    SessionPlayerScreen(
        state = uiState,
        actions =
            SessionPlayerActions(
                onWeightChange = viewModel::onWeightChange,
                onRirChange = viewModel::onRirChange,
                onRepsChange = viewModel::onRepsChange,
                onTick = viewModel::tickFocused,
                onRetry = viewModel::load,
                onClose = onClose,
            ),
    )
}
