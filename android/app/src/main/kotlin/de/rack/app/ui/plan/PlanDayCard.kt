package de.rack.app.ui.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import de.rack.app.domain.PlanDay
import de.rack.app.domain.PlanExercise
import de.rack.app.ui.logging.LoggingRow
import de.rack.app.ui.logging.LoggingSection
import de.rack.app.ui.logging.setCount
import de.rack.app.ui.theme.RecompTheme
import de.rack.app.ui.theme.SupersetKind
import de.rack.app.ui.theme.agentHighlight
import de.rack.app.ui.theme.tagColor

/**
 * A single plan day rendered per the prototype: a head band with a tag-colored
 * number chip, title, and focus, followed by its exercise groups. Each group is a
 * standalone exercise or a violet superset/circuit run. [highlightedIds] carries
 * the row ids an agent just touched; the matching head band or exercise row glows
 * transiently on the volt accent.
 */
@Composable
fun DayCard(
    dayContent: DayContent,
    logging: LoggingSection,
    highlightedIds: Set<String>,
    actions: DayCardActions,
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
        DayHead(
            day = dayContent.day,
            highlighted = dayContent.day.id in highlightedIds,
            onStartSession = { actions.onStartSession(dayContent.day.id) },
        )
        dayContent.groups.forEach { group ->
            if (group.kind != SupersetKind.NONE) {
                SupersetHeader(kind = group.kind)
            }
            group.exercises.forEach { exercise ->
                ExerciseRow(
                    exercise = exercise,
                    grouped = group.kind != SupersetKind.NONE,
                    logging = logging,
                    highlighted = exercise.id in highlightedIds,
                    onOpenExercise = actions.onOpenExercise,
                )
            }
        }
    }
}

/**
 * The day-card navigation callbacks, bundled so the card takes one parameter: opening a
 * tapped exercise's detail (by catalog exercise id) and starting a guided session for
 * the day (by `plan_day_id`).
 */
@Immutable
data class DayCardActions(
    val onOpenExercise: (String) -> Unit,
    val onStartSession: (String) -> Unit,
)

@Composable
private fun DayHead(
    day: PlanDay,
    highlighted: Boolean,
    onStartSession: () -> Unit,
) {
    val colors = RecompTheme.colors
    val type = RecompTheme.typography
    val spacing = RecompTheme.spacing
    val accent = colors.tagColor(day.tag)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(colors.panelElevated)
                .agentHighlight(highlighted = highlighted)
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
        Column(modifier = Modifier.weight(1f)) {
            Text(text = (day.title ?: "DAY").uppercase(), style = type.dayTitle, color = colors.txt)
            day.focus?.takeIf { it.isNotBlank() }?.let { focus ->
                Text(text = focus.uppercase(), style = type.label, color = colors.dim)
            }
        }
        Spacer(Modifier.width(spacing.md))
        StartSessionButton(onClick = onStartSession)
    }
}

@Composable
private fun StartSessionButton(onClick: () -> Unit) {
    val colors = RecompTheme.colors
    val type = RecompTheme.typography
    val spacing = RecompTheme.spacing
    Text(
        text = "START",
        style = type.label,
        color = colors.bg,
        modifier =
            Modifier
                .background(colors.volt, RecompTheme.shapes.md)
                .clickable(onClick = onClick)
                .padding(horizontal = spacing.lg, vertical = spacing.sm),
    )
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
    logging: LoggingSection,
    highlighted: Boolean,
    onOpenExercise: (String) -> Unit,
) {
    val colors = RecompTheme.colors
    val type = RecompTheme.typography
    val spacing = RecompTheme.spacing
    val sets = setCount(exercise.target)
    LaunchedEffect(exercise.id, sets) { logging.handlers.prepare(exercise.id, sets) }
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .then(if (grouped) Modifier.background(colors.supersetTint) else Modifier)
                .agentHighlight(highlighted = highlighted)
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
                modifier =
                    Modifier
                        .weight(1f)
                        .clickable { onOpenExercise(exercise.exerciseId) },
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
        logging.state.byExercise[exercise.id]?.let { state ->
            LoggingRow(
                exerciseId = exercise.id,
                state = state,
                unit = logging.state.weightUnit,
                handlers = logging.handlers,
            )
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
