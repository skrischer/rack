package de.rack.app.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import de.rack.app.di.AppContainer
import de.rack.app.di.appViewModelFactory
import de.rack.app.ui.LocalAppContainer
import de.rack.app.ui.RackDestinations
import java.time.LocalDate

/**
 * Registers the Home/overview destination, wiring its recent-session tap and the
 * standalone history entry to the calendar/history route (the latter optionally
 * deep-linked to a logged date). Extracted as a [NavGraphBuilder] extension so the
 * NavHost route table stays within the method-length guideline (Phase 11).
 */
fun NavGraphBuilder.homeDestination(navController: NavHostController) {
    composable(RackDestinations.HOME) {
        HomeRoute(
            container = LocalAppContainer.current,
            onBack = { navController.popBackStack() },
            onOpenSession = { date ->
                navController.navigate(RackDestinations.calendarRoute(date.toString()))
            },
            onOpenCalendar = { navController.navigate(RackDestinations.calendarRoute()) },
        )
    }
}

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
    onOpenCalendar: () -> Unit = {},
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
                onOpenCalendar = onOpenCalendar,
                onBack = onBack,
            ),
    )
}
