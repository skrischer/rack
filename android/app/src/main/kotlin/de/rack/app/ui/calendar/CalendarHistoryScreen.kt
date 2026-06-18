package de.rack.app.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
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
                is CalendarUiState.Loading -> CenterSpinner()
                is CalendarUiState.Empty -> EmptyState()
                is CalendarUiState.Error -> ErrorPane(message = state.message, onRetry = actions.onRetry)
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
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(colors.bg)
                .padding(horizontal = spacing.gutter, vertical = spacing.md),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = "HISTORY", style = type.kicker, color = colors.volt)
        Text(
            text = "BACK",
            style = type.label,
            color = colors.dim,
            modifier =
                Modifier
                    .border(spacing.border, colors.line, RecompTheme.shapes.sm)
                    .clickable(onClick = onBack)
                    .padding(horizontal = spacing.lg, vertical = spacing.sm),
        )
    }
}

@Composable
private fun EmptyState() {
    val colors = RecompTheme.colors
    val type = RecompTheme.typography
    val spacing = RecompTheme.spacing
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = spacing.gutter),
        verticalArrangement = Arrangement.spacedBy(spacing.sm, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "No training logged yet.",
            style = type.body,
            color = colors.mutedEmpty,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "Log a few sets and the days you trained appear here on the calendar.",
            style = type.body,
            color = colors.mutedEmpty,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun CenterSpinner() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = RecompTheme.colors.volt)
    }
}

@Composable
private fun ErrorPane(
    message: String,
    onRetry: () -> Unit,
) {
    val colors = RecompTheme.colors
    val type = RecompTheme.typography
    val spacing = RecompTheme.spacing
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = spacing.gutter),
        verticalArrangement = Arrangement.spacedBy(spacing.md, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = message, style = type.body, color = colors.legs, textAlign = TextAlign.Center)
        Text(
            text = "RETRY",
            style = type.label,
            color = colors.bg,
            modifier =
                Modifier
                    .background(colors.volt, RecompTheme.shapes.md)
                    .clickable(onClick = onRetry)
                    .padding(horizontal = spacing.lg, vertical = spacing.sm),
        )
    }
}
