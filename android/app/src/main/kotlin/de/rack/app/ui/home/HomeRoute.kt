package de.rack.app.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.rack.app.di.AppContainer
import de.rack.app.di.appViewModelFactory
import java.time.LocalDate

/**
 * Hosts the Home overview (Phase 11, docs/specs/spec-dashboards.md): resolves the
 * [HomeViewModel] from the [container], observes its state, and re-reads on every
 * resume so the overview reflects sets logged elsewhere (the spec's resume-refresh,
 * with no dedicated Realtime subscription). Recent-session taps carry the date to
 * [onOpenSession], which the calendar/history screen wires when it lands (#72).
 */
@Composable
fun HomeRoute(
    container: AppContainer,
    onBack: () -> Unit,
    onOpenSession: (LocalDate) -> Unit = {},
) {
    val viewModel: HomeViewModel = viewModel(factory = appViewModelFactory(container))
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleResumeEffect(viewModel) {
        viewModel.refresh()
        onPauseOrDispose { }
    }
    HomeScreen(
        state = uiState,
        actions =
            HomeActions(
                onRetry = viewModel::load,
                onOpenSession = onOpenSession,
                onBack = onBack,
            ),
    )
}
