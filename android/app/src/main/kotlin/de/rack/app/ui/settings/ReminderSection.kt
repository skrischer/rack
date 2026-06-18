package de.rack.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.rack.app.domain.ReminderPreferences
import de.rack.app.ui.theme.RecompTheme
import java.time.DayOfWeek

/**
 * The minimal in-app workout-reminder section (#66, docs/specs/spec-push-notifications.md):
 * an on/off toggle, a weekday chip row, and an hour/minute time stepper. Purely presentational —
 * it renders [prefs] and emits each edit upward; the scheduling lives in the ViewModel.
 */
@Composable
fun ReminderSection(
    prefs: ReminderPreferences,
    actions: ReminderActions,
) {
    SettingsSection(title = "WORKOUT REMINDERS") {
        SegmentedToggle(
            options =
                listOf(
                    ToggleOption(true, "ON"),
                    ToggleOption(false, "OFF"),
                ),
            selected = prefs.enabled,
            onSelect = actions.onSetEnabled,
        )
        WeekdayRow(selected = prefs.days, onToggle = actions.onToggleDay)
        TimeStepperRow(
            hour = prefs.hour,
            minute = prefs.minute,
            onSetTime = actions.onSetTime,
        )
    }
}

@Composable
private fun WeekdayRow(
    selected: Set<DayOfWeek>,
    onToggle: (DayOfWeek) -> Unit,
) {
    val spacing = RecompTheme.spacing
    Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
        WEEKDAYS.forEach { (day, label) ->
            DayChip(label = label, selected = day in selected, onClick = { onToggle(day) })
        }
    }
}

@Composable
private fun DayChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = RecompTheme.colors
    val spacing = RecompTheme.spacing
    val background = if (selected) colors.volt else colors.panelElevated
    val textColor = if (selected) colors.bg else colors.dim
    Box(
        modifier =
            Modifier
                .size(CHIP_SIZE)
                .background(background, RecompTheme.shapes.sm)
                .border(spacing.border, colors.line, RecompTheme.shapes.sm)
                .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, style = RecompTheme.typography.label, color = textColor)
    }
}

@Composable
private fun TimeStepperRow(
    hour: Int,
    minute: Int,
    onSetTime: (Int, Int) -> Unit,
) {
    val colors = RecompTheme.colors
    val type = RecompTheme.typography
    val spacing = RecompTheme.spacing
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = "TIME", style = type.label, color = colors.dim)
        Row(
            horizontalArrangement = Arrangement.spacedBy(spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TimeUnitStepper(
                value = hour,
                onChange = { onSetTime(it, minute) },
                wrap = HOURS_PER_DAY,
                step = 1,
            )
            Text(text = ":", style = type.loadValue, color = colors.volt)
            TimeUnitStepper(
                value = minute,
                onChange = { onSetTime(hour, it) },
                wrap = MINUTES_PER_HOUR,
                step = MINUTE_STEP,
            )
        }
    }
}

@Composable
private fun TimeUnitStepper(
    value: Int,
    onChange: (Int) -> Unit,
    wrap: Int,
    step: Int,
) {
    val colors = RecompTheme.colors
    val spacing = RecompTheme.spacing
    Row(
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TimeButton(symbol = "-", onClick = { onChange((value - step + wrap) % wrap) })
        Text(
            text = "%02d".format(value),
            style = RecompTheme.typography.loadValue,
            color = colors.volt,
        )
        TimeButton(symbol = "+", onClick = { onChange((value + step) % wrap) })
    }
}

@Composable
private fun TimeButton(
    symbol: String,
    onClick: () -> Unit,
) {
    val colors = RecompTheme.colors
    val spacing = RecompTheme.spacing
    Box(
        modifier =
            Modifier
                .size(CHIP_SIZE)
                .background(colors.panelElevated, RecompTheme.shapes.sm)
                .border(spacing.border, colors.line, RecompTheme.shapes.sm)
                .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = symbol, style = RecompTheme.typography.loadValue, color = colors.txt)
    }
}

private const val HOURS_PER_DAY = 24
private const val MINUTES_PER_HOUR = 60
private const val MINUTE_STEP = 5
private val CHIP_SIZE = 36.dp

/** The weekday chips in display order with their compact two-letter labels. */
private val WEEKDAYS =
    listOf(
        DayOfWeek.MONDAY to "MO",
        DayOfWeek.TUESDAY to "TU",
        DayOfWeek.WEDNESDAY to "WE",
        DayOfWeek.THURSDAY to "TH",
        DayOfWeek.FRIDAY to "FR",
        DayOfWeek.SATURDAY to "SA",
        DayOfWeek.SUNDAY to "SU",
    )
