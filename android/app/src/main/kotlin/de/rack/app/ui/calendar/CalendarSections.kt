package de.rack.app.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import de.rack.app.domain.LoggedExerciseEntry
import de.rack.app.domain.LoggedSetEntry
import de.rack.app.ui.theme.RecompTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/*
 * Presentational sections for the calendar/history screen (spec-dashboards.md, Phase 11):
 * the month navigation header and the selected-day disclosure that lists the day's logged
 * exercises with weight + per-set reps + RIR in kg (no unit toggle — Phase 12). Split from
 * CalendarHistoryScreen.kt to keep each file within the function-count guideline. No
 * business logic here — only render.
 */

private val SELECTED_DAY_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE dd.MM.yyyy")

/** Month label plus previous/next steppers, each disabled at the history range edge. */
@Composable
internal fun MonthNavigator(
    label: String,
    canGoBack: Boolean,
    canGoForward: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    val colors = RecompTheme.colors
    val type = RecompTheme.typography
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StepperButton(symbol = "‹", enabled = canGoBack, onClick = onPrevious)
        Text(text = label, style = type.dayTitle, color = colors.txt)
        StepperButton(symbol = "›", enabled = canGoForward, onClick = onNext)
    }
}

@Composable
private fun StepperButton(
    symbol: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = RecompTheme.colors
    val type = RecompTheme.typography
    val spacing = RecompTheme.spacing
    var modifier =
        Modifier
            .border(spacing.border, colors.line, RecompTheme.shapes.md)
            .padding(horizontal = spacing.lg, vertical = spacing.sm)
    if (enabled) modifier = Modifier.clickable(onClick = onClick).then(modifier)
    Text(
        text = symbol,
        style = type.dayTitle,
        color = if (enabled) colors.volt else colors.mutedEmpty,
        modifier = modifier,
    )
}

/** The selected day's disclosure: its date, then one card per logged exercise. */
@Composable
internal fun SelectedDayDetail(
    date: LocalDate,
    entries: List<LoggedExerciseEntry>,
) {
    val colors = RecompTheme.colors
    val type = RecompTheme.typography
    val spacing = RecompTheme.spacing
    Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
        Text(text = formatSelectedDay(date), style = type.kicker, color = colors.volt)
        if (entries.isEmpty()) {
            Text(text = "No sets logged on this day.", style = type.body, color = colors.mutedEmpty)
        } else {
            entries.forEach { entry -> ExerciseEntryCard(entry = entry) }
        }
    }
}

@Composable
private fun ExerciseEntryCard(entry: LoggedExerciseEntry) {
    val colors = RecompTheme.colors
    val type = RecompTheme.typography
    val spacing = RecompTheme.spacing
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(colors.panel, RecompTheme.shapes.xl)
                .border(spacing.border, colors.line, RecompTheme.shapes.xl)
                .padding(spacing.cardInsetH),
        verticalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        Text(text = entry.exerciseName, style = type.exerciseName, color = colors.txt)
        entry.sets.forEachIndexed { index, set ->
            Text(text = setLine(index = index, set = set), style = type.history, color = colors.dim)
        }
    }
}

/** "Set 1 · 100 kg · 5/5/5 · RIR 2" — weight/reps/RIR in the prototype's mono history voice. */
private fun setLine(
    index: Int,
    set: LoggedSetEntry,
): String {
    val weight = set.weight?.let { "${formatWeight(it)} kg" } ?: "BW"
    val reps = set.reps.filter { it > 0 }.joinToString(separator = "/").ifEmpty { "–" }
    val rir = set.rir?.let { " · RIR $it" }.orEmpty()
    return "SET ${index + 1} · $weight · $reps$rir"
}

private fun formatWeight(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()

private fun formatSelectedDay(date: LocalDate): String = date.format(SELECTED_DAY_FORMAT).uppercase()

/** "JUNE 2026" month label for the navigator header. */
internal fun monthLabel(
    year: Int,
    monthValue: Int
): String {
    val month = java.time.Month.of(monthValue).getDisplayName(TextStyle.FULL, Locale.getDefault())
    return "${month.uppercase()} $year"
}
