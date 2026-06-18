package de.rack.app.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

private val StepButtonSize = 32.dp
private val StepValueMinWidth = 54.dp

/**
 * The two-or-more-option segmented toggle from the kit (`.seg`): a single bordered,
 * rounded track whose active cell fills volt with [RecompColors.bg] text while the rest stay
 * dim on panel. Drive it with the selected index; emits [onSelect] with the tapped index.
 *
 * Presentational: the caller owns the selection state.
 */
@Composable
fun RecompSegmentedToggle(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = RecompTheme.colors
    val spacing = RecompTheme.spacing
    Row(
        modifier =
            modifier
                .clip(RecompTheme.shapes.sm)
                .border(spacing.border, colors.line, RecompTheme.shapes.sm),
    ) {
        options.forEachIndexed { index, option ->
            SegmentCell(label = option, selected = index == selectedIndex, onClick = { onSelect(index) })
        }
    }
}

@Composable
private fun SegmentCell(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = RecompTheme.colors
    val spacing = RecompTheme.spacing
    val background = if (selected) colors.volt else colors.panel
    val content = if (selected) colors.bg else colors.dim
    val weight = if (selected) FontWeight.SemiBold else FontWeight.Normal
    Box(
        modifier =
            Modifier
                .recompClick(onClick = onClick)
                .background(background)
                .padding(horizontal = spacing.lg, vertical = spacing.sm),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label.uppercase(), style = RecompTheme.typography.label.copy(fontWeight = weight), color = content)
    }
}

/**
 * The increment/decrement stepper from the kit (`.stepper`): a `−` and `+` square button
 * flanking a fixed-width mono value (e.g. a rest-time default like `2:30`). Presentational —
 * the caller formats [value] and applies the step in [onDecrement] / [onIncrement].
 */
@Composable
fun RecompStepper(
    value: String,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = RecompTheme.colors
    val spacing = RecompTheme.spacing
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StepButton(glyph = "−", onClick = onDecrement)
        Text(
            text = value,
            style = RecompTheme.typography.body.copy(fontFamily = SplineSansMonoFamily),
            color = colors.txt,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(min = StepValueMinWidth),
        )
        StepButton(glyph = "+", onClick = onIncrement)
    }
}

@Composable
private fun StepButton(
    glyph: String,
    onClick: () -> Unit,
) {
    val colors = RecompTheme.colors
    val spacing = RecompTheme.spacing
    Box(
        modifier =
            Modifier
                .size(StepButtonSize)
                .recompPress(onClick = onClick)
                .border(spacing.border, colors.line, RecompTheme.shapes.sm)
                .background(colors.panel, RecompTheme.shapes.sm),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = glyph,
            style = RecompTheme.typography.exerciseName.copy(fontFamily = SplineSansMonoFamily),
            color = colors.volt,
        )
    }
}
