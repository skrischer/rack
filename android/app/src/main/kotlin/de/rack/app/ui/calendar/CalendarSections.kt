package de.rack.app.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.em
import de.rack.app.domain.LoggedExerciseEntry
import de.rack.app.domain.LoggedSetEntry
import de.rack.app.ui.theme.RecompBadge
import de.rack.app.ui.theme.RecompBadgeStyle
import de.rack.app.ui.theme.RecompDivider
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
        Text(
            text = label,
            style = type.label.copy(letterSpacing = 0.1.em),
            color = colors.txt,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
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
            .border(spacing.border, colors.line, RecompTheme.shapes.sm)
            .padding(horizontal = spacing.md, vertical = spacing.sm)
    if (enabled) modifier = Modifier.clickable(onClick = onClick).then(modifier)
    Text(
        text = symbol,
        style = type.loadValue,
        color = if (enabled) colors.dim else colors.mutedEmpty,
        modifier = modifier,
    )
}

/** The selected day's disclosure: a section label, then one card listing the day's exercises. */
@Composable
internal fun SelectedDayDetail(
    date: LocalDate,
    entries: List<LoggedExerciseEntry>,
) {
    val colors = RecompTheme.colors
    val type = RecompTheme.typography
    val spacing = RecompTheme.spacing
    Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
        Text(
            text = "Geloggte Einheit".uppercase(),
            style = type.label.copy(letterSpacing = 0.12.em),
            color = colors.dim,
        )
        if (entries.isEmpty()) {
            Text(text = "Keine Sätze an diesem Tag.", style = type.body, color = colors.mutedEmpty)
        } else {
            SessionCard(date = date, entries = entries)
        }
    }
}

/** The kit `.card`: a head band (date + exercise count) over hairline-separated exercise rows. */
@Composable
private fun SessionCard(
    date: LocalDate,
    entries: List<LoggedExerciseEntry>,
) {
    val colors = RecompTheme.colors
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RecompTheme.shapes.xl)
                .background(colors.panel)
                .border(RecompTheme.spacing.border, colors.line, RecompTheme.shapes.xl),
    ) {
        SessionHead(date = date, exerciseCount = entries.size)
        entries.forEach { entry ->
            RecompDivider()
            ExerciseEntryRow(entry = entry)
        }
    }
}

@Composable
private fun SessionHead(
    date: LocalDate,
    exerciseCount: Int,
) {
    val colors = RecompTheme.colors
    val type = RecompTheme.typography
    val spacing = RecompTheme.spacing
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(colors.panelElevated)
                .padding(horizontal = spacing.cardInsetH, vertical = spacing.lg),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = formatSelectedDay(date),
            style = type.loadValue,
            color = colors.txt,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(spacing.md))
        RecompBadge(text = exerciseCountLabel(exerciseCount), style = RecompBadgeStyle.Volt)
    }
}

/** One logged exercise: its name on the left, one mono load line per logged set on the right. */
@Composable
private fun ExerciseEntryRow(entry: LoggedExerciseEntry) {
    val colors = RecompTheme.colors
    val type = RecompTheme.typography
    val spacing = RecompTheme.spacing
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.cardInsetH, vertical = spacing.rowInsetV),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Text(text = entry.exerciseName, style = type.exerciseName, color = colors.txt, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(spacing.md))
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
            entry.sets.forEach { set -> LoadLine(set = set) }
        }
    }
}

/** "85 kg · 8/7/7/6 · RIR 1" — weight/middots in txt, reps in volt, RIR in dim (kit `.ex-load`). */
@Composable
private fun LoadLine(set: LoggedSetEntry) {
    val colors = RecompTheme.colors
    val style = RecompTheme.typography.loadValue
    val reps = set.reps.filter { it > 0 }.joinToString(separator = "/").ifEmpty { "–" }
    val weight = set.weight?.let { "${formatWeight(it)} kg" } ?: "BW"
    Row(verticalAlignment = Alignment.Bottom) {
        Text(text = "$weight · ", style = style, color = colors.txt)
        Text(text = reps, style = style, color = colors.volt)
        set.rir?.let { rir -> Text(text = " · RIR $rir", style = style, color = colors.dim) }
    }
}

/** German exercise-count badge copy with singular/plural agreement. */
private fun exerciseCountLabel(count: Int): String = if (count == 1) "1 Übung" else "$count Übungen"

private fun formatWeight(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()

private fun formatSelectedDay(date: LocalDate): String = date.format(SELECTED_DAY_FORMAT).uppercase()

/** "JUNE 2026" month label for the navigator header. */
internal fun monthLabel(
    year: Int,
    monthValue: Int,
): String {
    val month = java.time.Month.of(monthValue).getDisplayName(TextStyle.FULL, Locale.getDefault())
    return "${month.uppercase()} $year"
}
