package de.rack.app.ui.session

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import de.rack.app.ui.theme.RecompTheme
import de.rack.app.ui.theme.SupersetKind

/**
 * The focused exercise/set card: the group/progress header, the exercise name and its
 * read-only plan target (sets × reps, RIR, cue), the "last time" reference, the set
 * tick strip, one per-exercise kg and one per-exercise RIR field, the focused set's
 * editable reps, the superset/circuit "Next: <exercise>" rotation cue, and the tick
 * action. Renders [content] and forwards events through [actions]; no logic here —
 * kg/RIR are single per-exercise values, reps are per-set, per the spec.
 */
@Composable
fun SessionFocusCard(
    content: SessionFocusContent,
    actions: SessionPlayerActions,
) {
    val spacing = RecompTheme.spacing
    val step = content.step
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.gutter, vertical = spacing.lg),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        FocusHeader(step = step, progress = content.progress)
        ExerciseHeading(step = step)
        step.cue?.takeIf { it.isNotBlank() }?.let { CueLine(cue = it) }
        ReferenceLine(reference = content.reference)
        SetStrip(step = step)
        PerExerciseInputs(
            entries = content.entries,
            onWeightChange = actions.onWeightChange,
            onRirChange = actions.onRirChange,
        )
        RepsInput(step = step, value = content.entries.reps[step.setIndex].orEmpty(), onChange = actions.onRepsChange)
        content.rotationCueName?.let { RotationCue(kind = step.kind, name = it) }
        TickButton(onTick = actions.onTick)
    }
}

@Composable
private fun FocusHeader(
    step: SessionStep,
    progress: SessionProgress,
) {
    val colors = RecompTheme.colors
    val type = RecompTheme.typography
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text = kindLabel(step.kind), style = type.kicker, color = groupColor(step.kind))
        Text(text = "${progress.done} / ${progress.total}", style = type.label, color = colors.dim)
    }
}

@Composable
private fun ExerciseHeading(step: SessionStep) {
    val colors = RecompTheme.colors
    val type = RecompTheme.typography
    val spacing = RecompTheme.spacing
    Column(verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
        Text(text = step.name, style = type.dayTitle, color = colors.txt)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = "SET ${step.setIndex + 1} / ${step.totalSets}", style = type.label, color = colors.volt)
            step.target?.takeIf { it.isNotBlank() }?.let { target ->
                Text(text = "   $target", style = type.loadValue, color = colors.dim)
            }
            step.rir?.let { rir ->
                Text(text = "   RIR $rir", style = type.loadValue, color = colors.dim)
            }
        }
    }
}

@Composable
private fun CueLine(cue: String) {
    val colors = RecompTheme.colors
    val type = RecompTheme.typography
    Row {
        Text(text = "→ ", style = type.cue, color = colors.voltDim)
        Text(text = cue, style = type.cue, color = colors.dim)
    }
}

@Composable
private fun ReferenceLine(reference: String?) {
    val colors = RecompTheme.colors
    val type = RecompTheme.typography
    val text = reference?.let { "Last time · $it" } ?: "No entry yet"
    Text(text = text, style = type.lastTime, color = if (reference != null) colors.dim else colors.mutedEmpty)
}

@Composable
private fun SetStrip(step: SessionStep) {
    val spacing = RecompTheme.spacing
    Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
        repeat(step.totalSets) { index ->
            SetChip(index = index, done = index < step.setIndex, focused = index == step.setIndex)
        }
    }
}

@Composable
private fun SetChip(
    index: Int,
    done: Boolean,
    focused: Boolean,
) {
    val colors = RecompTheme.colors
    val type = RecompTheme.typography
    val spacing = RecompTheme.spacing
    val background =
        when {
            focused -> colors.volt
            done -> colors.voltDim
            else -> colors.panel
        }
    val textColor = if (focused || done) colors.bg else colors.dim
    Text(
        text = "${index + 1}",
        style = type.label,
        color = textColor,
        modifier =
            Modifier
                .size(spacing.dayChip)
                .background(background, RecompTheme.shapes.md)
                .border(spacing.border, colors.line, RecompTheme.shapes.md)
                .padding(vertical = spacing.sm),
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun RotationCue(
    kind: SupersetKind,
    name: String,
) {
    val type = RecompTheme.typography
    val spacing = RecompTheme.spacing
    val accent = groupColor(kind)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(spacing.sm).background(accent, RecompTheme.shapes.sm))
        Spacer(Modifier.width(spacing.sm))
        Text(text = "Next: $name", style = type.label, color = accent)
    }
}

@Composable
private fun groupColor(kind: SupersetKind): Color =
    if (kind == SupersetKind.NONE) RecompTheme.colors.volt else RecompTheme.colors.superset

private fun kindLabel(kind: SupersetKind): String =
    when (kind) {
        SupersetKind.NONE -> "EXERCISE"
        SupersetKind.SUPERSET -> "SUPERSET"
        SupersetKind.CIRCUIT -> "CIRCUIT"
    }
