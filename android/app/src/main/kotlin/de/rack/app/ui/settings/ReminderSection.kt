package de.rack.app.ui.settings

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import de.rack.app.domain.ReminderPreferences
import de.rack.app.ui.theme.RecompChip
import de.rack.app.ui.theme.RecompChipRow
import de.rack.app.ui.theme.RecompTheme
import de.rack.app.ui.theme.recompClick
import java.time.DayOfWeek

/**
 * The in-app workout-reminder card (#66, docs/specs/spec-push-notifications.md), re-skinned
 * to the Recomp `settings.html` reference: an on/off [ReminderSwitch], a weekday chip rail,
 * and an hour/minute time stepper. Purely presentational — it renders [prefs] and emits each
 * edit upward; the scheduling lives in the ViewModel.
 */
@Composable
fun ReminderSection(
    prefs: ReminderPreferences,
    actions: ReminderActions,
) {
    SettingsCard(title = "Erinnerung") {
        SettingsRow(label = "Aktiviert") {
            ReminderSwitch(on = prefs.enabled, onToggle = { actions.onSetEnabled(!prefs.enabled) })
        }
        SettingsBlock {
            WeekdayChips(selected = prefs.days, onToggle = actions.onToggleDay)
        }
        SettingsRow(label = "Uhrzeit") {
            TimeControl(hour = prefs.hour, minute = prefs.minute, onSetTime = actions.onSetTime)
        }
    }
}

/**
 * The kit `.switch` pill toggle: a rounded track that fills volt when [on], with a thumb that
 * slides from the left inset to the right inset. Indication-less tap flips it via [onToggle].
 */
@Composable
private fun ReminderSwitch(
    on: Boolean,
    onToggle: () -> Unit,
) {
    val colors = RecompTheme.colors
    val track = if (on) colors.volt else colors.line
    val thumb = if (on) colors.bg else colors.txt
    val thumbX by animateDpAsState(
        targetValue = if (on) SWITCH_WIDTH - SWITCH_THUMB - SWITCH_INSET else SWITCH_INSET,
        label = "reminderSwitchThumb",
    )
    Box(
        modifier =
            Modifier
                .size(width = SWITCH_WIDTH, height = SWITCH_HEIGHT)
                .recompClick(onClick = onToggle)
                .clip(CircleShape)
                .background(track),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier =
                Modifier
                    .offset(x = thumbX)
                    .size(SWITCH_THUMB)
                    .clip(CircleShape)
                    .background(thumb),
        )
    }
}

@Composable
private fun WeekdayChips(
    selected: Set<DayOfWeek>,
    onToggle: (DayOfWeek) -> Unit,
) {
    RecompChipRow {
        WEEKDAYS.forEach { (day, label) ->
            RecompChip(label = label, selected = day in selected, onClick = { onToggle(day) })
        }
    }
}

@Composable
private fun TimeControl(
    hour: Int,
    minute: Int,
    onSetTime: (Int, Int) -> Unit,
) {
    val colors = RecompTheme.colors
    val type = RecompTheme.typography
    val spacing = RecompTheme.spacing
    Row(
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TimeUnitStepper(value = hour, onChange = { onSetTime(it, minute) }, wrap = HOURS_PER_DAY, step = 1)
        Text(text = ":", style = type.loadValue, color = colors.volt)
        TimeUnitStepper(value = minute, onChange = { onSetTime(hour, it) }, wrap = MINUTES_PER_HOUR, step = MINUTE_STEP)
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
        TimeButton(symbol = "−", onClick = { onChange((value - step + wrap) % wrap) })
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
                .size(STEP_BUTTON_SIZE)
                .recompClick(onClick = onClick)
                .border(spacing.border, colors.line, RecompTheme.shapes.sm)
                .background(colors.panel, RecompTheme.shapes.sm),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = symbol, style = RecompTheme.typography.loadValue, color = colors.volt)
    }
}

private const val HOURS_PER_DAY = 24
private const val MINUTES_PER_HOUR = 60
private const val MINUTE_STEP = 5

private val STEP_BUTTON_SIZE = 32.dp
private val SWITCH_WIDTH = 42.dp
private val SWITCH_HEIGHT = 24.dp
private val SWITCH_THUMB = 18.dp
private val SWITCH_INSET = 3.dp

/** The weekday chips in display order with their compact German two-letter labels. */
private val WEEKDAYS =
    listOf(
        DayOfWeek.MONDAY to "Mo",
        DayOfWeek.TUESDAY to "Di",
        DayOfWeek.WEDNESDAY to "Mi",
        DayOfWeek.THURSDAY to "Do",
        DayOfWeek.FRIDAY to "Fr",
        DayOfWeek.SATURDAY to "Sa",
        DayOfWeek.SUNDAY to "So",
    )
