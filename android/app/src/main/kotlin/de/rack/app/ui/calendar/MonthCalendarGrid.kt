package de.rack.app.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.rack.app.ui.theme.RecompTheme
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle

// Kit `.cal-cell.marked .dot` geometry: a 5px volt dot sitting 3px under the day number.
private val MarkedDotSize = 5.dp
private val MarkedDotGap = 3.dp

/**
 * A read-only month calendar grid (Phase 11, docs/specs/spec-dashboards.md). Renders the
 * weeks of [month] as a 7-column Monday-start grid; days present in [loggedDates] are
 * marked with the volt accent, the [selectedDate] is outlined, and tapping any day raises
 * [onSelectDate]. Purely presentational — the marking set and selection live in the
 * [CalendarViewModel].
 */
@Composable
internal fun MonthCalendarGrid(
    month: YearMonth,
    loggedDates: Set<LocalDate>,
    selectedDate: LocalDate?,
    onSelectDate: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = RecompTheme.spacing
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
        WeekdayHeader()
        weeksOf(month).forEach { week ->
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs), modifier = Modifier.fillMaxWidth()) {
                week.forEach { day ->
                    DayCell(
                        day = day,
                        isLogged = day != null && day in loggedDates,
                        isSelected = day != null && day == selectedDate,
                        onSelectDate = onSelectDate,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun WeekdayHeader() {
    val colors = RecompTheme.colors
    val caption = RecompTheme.typography.caption
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(RecompTheme.spacing.xs)) {
        WEEKDAY_ORDER.forEach { weekday ->
            Text(
                text = weekday.getDisplayName(TextStyle.SHORT, UI_LOCALE),
                style = caption,
                color = colors.dim,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun DayCell(
    day: LocalDate?,
    isLogged: Boolean,
    isSelected: Boolean,
    onSelectDate: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = RecompTheme.colors
    val type = RecompTheme.typography
    val spacing = RecompTheme.spacing
    // Selected wins (volt fill); a logged-but-unselected day reads as a panel chip with
    // a line border and a volt dot, matching kit `.cal-cell.sel` vs `.cal-cell.marked`.
    // clip + clickable last so the tap ripple is confined to the rounded cell.
    var cell = modifier.aspectRatio(1f).clip(RecompTheme.shapes.sm)
    cell =
        when {
            isSelected -> cell.background(colors.volt)
            isLogged -> cell.background(colors.panel).border(spacing.border, colors.line, RecompTheme.shapes.sm)
            else -> cell
        }
    if (day != null) cell = cell.clickable { onSelectDate(day) }
    Column(
        modifier = cell,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (day != null) {
            Text(
                text = day.dayOfMonth.toString(),
                style = type.loadValue.copy(fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal),
                color = if (isSelected) colors.bg else colors.txt,
            )
            if (isLogged && !isSelected) {
                Spacer(Modifier.height(MarkedDotGap))
                Box(modifier = Modifier.size(MarkedDotSize).background(colors.volt, CircleShape))
            }
        }
    }
}

/** The seven weekday columns in Monday-start order to match the ISO week boundary. */
private val WEEKDAY_ORDER: List<DayOfWeek> =
    listOf(
        DayOfWeek.MONDAY,
        DayOfWeek.TUESDAY,
        DayOfWeek.WEDNESDAY,
        DayOfWeek.THURSDAY,
        DayOfWeek.FRIDAY,
        DayOfWeek.SATURDAY,
        DayOfWeek.SUNDAY,
    )

/**
 * The weeks of [month] as rows of seven nullable days (Monday start): leading and
 * trailing nulls pad the partial first and last weeks so the grid stays a clean 7×N.
 */
private fun weeksOf(month: YearMonth): List<List<LocalDate?>> {
    val first = month.atDay(1)
    val leadingBlanks = (first.dayOfWeek.value - DayOfWeek.MONDAY.value + DAYS_IN_WEEK) % DAYS_IN_WEEK
    val cells = ArrayList<LocalDate?>()
    repeat(leadingBlanks) { cells.add(null) }
    for (dayOfMonth in 1..month.lengthOfMonth()) cells.add(month.atDay(dayOfMonth))
    while (cells.size % DAYS_IN_WEEK != 0) cells.add(null)
    return cells.chunked(DAYS_IN_WEEK)
}

private const val DAYS_IN_WEEK = 7
