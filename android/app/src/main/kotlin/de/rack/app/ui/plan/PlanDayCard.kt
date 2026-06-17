package de.rack.app.ui.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import de.rack.app.domain.PlanDay
import de.rack.app.domain.PlanExercise
import de.rack.app.ui.theme.RecompTheme
import de.rack.app.ui.theme.SupersetKind
import de.rack.app.ui.theme.tagColor

/**
 * A single plan day rendered per the prototype: a head band with a tag-colored
 * number chip, title, and focus, followed by its exercise groups. Each group is a
 * standalone exercise or a violet superset/circuit run.
 */
@Composable
fun DayCard(
    dayContent: DayContent,
    modifier: Modifier = Modifier,
) {
    val colors = RecompTheme.colors
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .background(colors.panel, RecompTheme.shapes.xl)
                .border(RecompTheme.spacing.border, colors.line, RecompTheme.shapes.xl),
    ) {
        DayHead(day = dayContent.day)
        dayContent.groups.forEach { group ->
            if (group.kind != SupersetKind.NONE) {
                SupersetHeader(kind = group.kind)
            }
            group.exercises.forEach { exercise ->
                ExerciseRow(exercise = exercise, grouped = group.kind != SupersetKind.NONE)
            }
        }
    }
}

@Composable
private fun DayHead(day: PlanDay) {
    val colors = RecompTheme.colors
    val type = RecompTheme.typography
    val spacing = RecompTheme.spacing
    val accent = colors.tagColor(day.tag)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(colors.panelElevated)
                .padding(horizontal = spacing.cardInsetH, vertical = spacing.xl),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(spacing.dayChip).background(accent, RecompTheme.shapes.md),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = dayNumber(day.position), style = type.loadValue, color = colors.bg)
        }
        Spacer(Modifier.width(spacing.md))
        Column {
            Text(text = (day.title ?: "DAY").uppercase(), style = type.dayTitle, color = colors.txt)
            day.focus?.takeIf { it.isNotBlank() }?.let { focus ->
                Text(text = focus.uppercase(), style = type.label, color = colors.dim)
            }
        }
    }
}

@Composable
private fun SupersetHeader(kind: SupersetKind) {
    val colors = RecompTheme.colors
    val type = RecompTheme.typography
    val spacing = RecompTheme.spacing
    val labelText = if (kind == SupersetKind.CIRCUIT) "CIRCUIT" else "SUPERSET"
    Text(
        text = labelText,
        style = type.supersetHeader,
        color = colors.superset,
        modifier =
            Modifier.padding(
                start = spacing.cardInsetH,
                top = spacing.md,
                bottom = spacing.xxs,
            ),
    )
}

@Composable
private fun ExerciseRow(
    exercise: PlanExercise,
    grouped: Boolean,
) {
    val colors = RecompTheme.colors
    val type = RecompTheme.typography
    val spacing = RecompTheme.spacing
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .then(if (grouped) Modifier.background(colors.supersetTint) else Modifier)
                .padding(horizontal = spacing.cardInsetH, vertical = spacing.rowInsetV),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Text(
                text = exercise.name,
                style = type.exerciseName,
                color = colors.txt,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(spacing.md))
            TargetBadge(target = exercise.target, rir = exercise.rir)
        }
        exercise.cue?.takeIf { it.isNotBlank() }?.let { cue ->
            Row(modifier = Modifier.padding(top = spacing.xs)) {
                Text(text = "→ ", style = type.cue, color = colors.voltDim)
                Text(text = cue, style = type.cue, color = colors.dim)
            }
        }
    }
}

@Composable
private fun TargetBadge(
    target: String?,
    rir: Int?,
) {
    val colors = RecompTheme.colors
    val type = RecompTheme.typography
    Row {
        target?.takeIf { it.isNotBlank() }?.let { value ->
            Text(text = value, style = type.loadValue, color = colors.volt)
        }
        rir?.let { value ->
            Text(text = "  RIR $value", style = type.loadValue, color = colors.dim)
        }
    }
}

/** Two-digit day label from the 1-based [position] (e.g. position 1 -> "01"). */
private fun dayNumber(position: Int): String = position.toString().padStart(2, '0')
