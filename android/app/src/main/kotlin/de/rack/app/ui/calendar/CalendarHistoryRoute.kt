package de.rack.app.ui.calendar

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import de.rack.app.di.AppContainer
import de.rack.app.di.calendarViewModelFactory
import de.rack.app.ui.LocalAppContainer
import de.rack.app.ui.RackDestinations
import java.time.LocalDate

/**
 * Registers the calendar/history destination, whose [RackDestinations.CALENDAR_DATE_ARG]
 * is an optional ISO-date deep link from a Home recent-session row. Extracted as a
 * [NavGraphBuilder] extension so the NavHost route table stays within the method-length
 * guideline (Phase 11).
 */
fun NavGraphBuilder.calendarDestination(navController: NavHostController) {
    composable(
        route = RackDestinations.CALENDAR,
        arguments =
            listOf(
                navArgument(RackDestinations.CALENDAR_DATE_ARG) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
    ) { entry ->
        val date =
            entry.arguments
                ?.getString(RackDestinations.CALENDAR_DATE_ARG)
                ?.let(LocalDate::parse)
        CalendarHistoryRoute(
            container = LocalAppContainer.current,
            onBack = { navController.popBackStack() },
            initialDate = date,
        )
    }
}

/**
 * Hosts the calendar/history screen (Phase 11, docs/specs/spec-dashboards.md): resolves
 * the route-scoped [CalendarViewModel] from the [container], optionally seeded with the
 * Home recent-session deep link [initialDate], observes its state, and re-reads on every
 * resume so the marked days reflect sets logged elsewhere (the spec's resume-refresh, no
 * dedicated Realtime subscription). Kept out of the NavHost so the graph stays a thin
 * route table.
 */
@Composable
fun CalendarHistoryRoute(
    container: AppContainer,
    onBack: () -> Unit,
    initialDate: LocalDate? = null,
) {
    val factory = calendarViewModelFactory(container, initialDate)
    val viewModel: CalendarViewModel = viewModel(key = initialDate?.toString().orEmpty(), factory = factory)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleResumeEffect(viewModel) {
        viewModel.refresh()
        onPauseOrDispose { }
    }
    CalendarHistoryScreen(
        state = uiState,
        actions =
            CalendarActions(
                onRetry = viewModel::load,
                onShowMonth = viewModel::showMonth,
                onSelectDate = viewModel::selectDate,
                onBack = onBack,
            ),
    )
}
