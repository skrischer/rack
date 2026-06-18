package de.rack.app.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import de.rack.app.ui.theme.RecompDivider
import de.rack.app.ui.theme.RecompEmpty
import de.rack.app.ui.theme.RecompError
import de.rack.app.ui.theme.RecompLoading
import de.rack.app.ui.theme.RecompTheme
import java.time.LocalDate
import java.time.YearMonth

/**
 * Read-only calendar/history screen (docs/specs/spec-dashboards.md, Phase 11). It marks
 * exactly the dates that have logged sets on a Monday-start month grid, expands a tapped
 * marked day to its logged exercises (weight + per-set reps + RIR), and steps months
 * across the full logged history — rendering empty months without a chart and an explicit
 * Recomp empty state on zero data. Purely presentational: the read, marking, range, and
 * selection live in [CalendarViewModel].
 */
@Composable
fun CalendarHistoryScreen(
    state: CalendarUiState,
    actions: CalendarActions,
    modifier: Modifier = Modifier,
) {
    val colors = RecompTheme.colors
    Column(modifier = modifier.fillMaxSize().background(colors.bg)) {
        CalendarTopBar(onBack = actions.onBack)
        Box(modifier = Modifier.weight(1f)) {
            when (state) {
                is CalendarUiState.Loading -> CenteredState { RecompLoading() }
                is CalendarUiState.Empty -> CenteredState { EmptyState() }
                is CalendarUiState.Error ->
                    CenteredState {
                        RecompError(
                            message = state.message,
                            onRetry = actions.onRetry
                        )
                    }
                is CalendarUiState.Content -> CalendarContentPane(content = state.content, actions = actions)
            }
        }
    }
}

/** Calendar actions bundled so the screen takes one parameter for navigation and selection. */
@Immutable
data class CalendarActions(
    val onRetry: () -> Unit,
    val onShowMonth: (YearMonth) -> Unit,
    val onSelectDate: (LocalDate) -> Unit,
    val onBack: () -> Unit,
)

@Composable
private fun CalendarContentPane(
    content: CalendarContent,
    actions: CalendarActions,
) {
    val spacing = RecompTheme.spacing
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = spacing.gutter, vertical = spacing.lg),
        verticalArrangement = Arrangement.spacedBy(spacing.lg),
    ) {
        item {
            MonthNavigator(
                label = monthLabel(content.month.year, content.month.monthValue),
                canGoBack = content.month.isAfter(content.range.earliest),
                canGoForward = content.month.isBefore(content.range.latest),
                onPrevious = { actions.onShowMonth(content.month.minusMonths(1)) },
                onNext = { actions.onShowMonth(content.month.plusMonths(1)) },
            )
        }
        item {
            MonthCalendarGrid(
                month = content.month,
                loggedDates = content.loggedDates,
                selectedDate = content.selectedDate,
                onSelectDate = actions.onSelectDate,
            )
        }
        content.selectedDate?.let { date ->
            item { SelectedDayDetail(date = date, entries = content.selectedDetail) }
        }
    }
}

@Composable
private fun CalendarTopBar(onBack: () -> Unit) {
    val colors = RecompTheme.colors
    val type = RecompTheme.typography
    val spacing = RecompTheme.spacing
    Column {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(colors.bg)
                    .padding(horizontal = spacing.gutter, vertical = spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "‹",
                style = type.loadValue,
                color = colors.dim,
                modifier =
                    Modifier
                        .border(spacing.border, colors.line, RecompTheme.shapes.sm)
                        .clickable(onClick = onBack)
                        .padding(horizontal = spacing.md, vertical = spacing.sm),
            )
            Spacer(Modifier.width(spacing.md))
            Text(text = "Verlauf".uppercase(), style = type.kicker, color = colors.volt)
        }
        RecompDivider()
    }
}

/** Vertically centers a shared state component within the content area below the top bar. */
@Composable
private fun CenteredState(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}

@Composable
private fun EmptyState() {
    RecompEmpty(text = "Noch kein Training geloggt.\nLogge ein paar Sätze und die Trainingstage erscheinen hier.")
}
