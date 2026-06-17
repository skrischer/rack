package de.rack.app.ui.session

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import de.rack.app.ui.theme.RecompTheme

/**
 * The session player's editable inputs: one per-exercise kg field and one per-exercise
 * RIR field (single values applied to every ticked set of the focused exercise, never
 * per-set), the focused set's editable reps field, and the one-tap tick action. Purely
 * presentational — each field renders its value and forwards edits upward; the spec
 * forbids any per-set kg or per-set RIR input here.
 */
@Composable
internal fun PerExerciseInputs(
    entries: ExerciseEntries,
    onWeightChange: (String) -> Unit,
    onRirChange: (String) -> Unit,
) {
    val spacing = RecompTheme.spacing
    Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm), verticalAlignment = Alignment.Bottom) {
        SessionField(label = "KG", value = entries.weight, keyboard = KeyboardType.Decimal, onChange = onWeightChange)
        SessionField(label = "RIR", value = entries.rir, keyboard = KeyboardType.Number, onChange = onRirChange)
    }
}

@Composable
internal fun RepsInput(
    step: SessionStep,
    value: String,
    onChange: (String) -> Unit,
) {
    Row(verticalAlignment = Alignment.Bottom) {
        SessionField(
            label = "REPS · SET ${step.setIndex + 1}",
            value = value,
            keyboard = KeyboardType.Number,
            onChange = onChange,
        )
    }
}

@Composable
internal fun TickButton(onTick: () -> Unit) {
    val colors = RecompTheme.colors
    val type = RecompTheme.typography
    val spacing = RecompTheme.spacing
    Text(
        text = "✓ TICK SET",
        style = type.label,
        color = colors.bg,
        modifier =
            Modifier
                .fillMaxWidth()
                .background(colors.volt, RecompTheme.shapes.md)
                .clickable(onClick = onTick)
                .padding(vertical = spacing.md),
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun SessionField(
    label: String,
    value: String,
    keyboard: KeyboardType,
    onChange: (String) -> Unit,
) {
    val colors = RecompTheme.colors
    val type = RecompTheme.typography
    val spacing = RecompTheme.spacing
    Column(
        modifier = Modifier.width(spacing.dayChip + spacing.huge),
        verticalArrangement = Arrangement.spacedBy(spacing.xxs),
    ) {
        Text(text = label, style = type.caption, color = colors.dim)
        TextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = LocalTextStyle.current.merge(type.intro.copy(color = colors.txt)),
            keyboardOptions = KeyboardOptions(keyboardType = keyboard),
            colors = sessionFieldColors(),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun sessionFieldColors() =
    TextFieldDefaults.colors(
        focusedContainerColor = RecompTheme.colors.bg,
        unfocusedContainerColor = RecompTheme.colors.bg,
        disabledContainerColor = RecompTheme.colors.bg,
        focusedIndicatorColor = RecompTheme.colors.volt,
        unfocusedIndicatorColor = RecompTheme.colors.line,
        cursorColor = RecompTheme.colors.volt,
    )
