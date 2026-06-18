package de.rack.app.ui.plate

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
import de.rack.app.domain.PlateCalcPreferences
import de.rack.app.domain.formatPlateKg
import de.rack.app.ui.theme.RecompTheme

/**
 * The persisted bar-weight + inventory editors for the plate calculator (#83,
 * docs/specs/spec-plate-calc-1rm.md). Each stepper edit is forwarded upward to the
 * ViewModel, which clamps it and persists it via the repository; this file is purely
 * presentational. Kept separate from [PlateCalcSections] so neither file overflows the
 * per-file function-count guideline.
 */
@Composable
fun PreferencesSection(
    preferences: PlateCalcPreferences,
    onBarWeightChange: (Double) -> Unit,
    onPairCountChange: (Double, Int) -> Unit,
) {
    PlateSection(title = "BAR & PLATES") {
        StepperRow(
            label = "BAR KG",
            value = formatPlateKg(preferences.barWeightKg.toBigDecimal()),
            onDecrement = { onBarWeightChange(-BAR_STEP_KG) },
            onIncrement = { onBarWeightChange(BAR_STEP_KG) },
        )
        preferences.inventory.forEach { stock ->
            StepperRow(
                label = "${formatPlateKg(stock.plateKg.toBigDecimal())} KG",
                value = "${stock.pairCount}×",
                onDecrement = { onPairCountChange(stock.plateKg, -1) },
                onIncrement = { onPairCountChange(stock.plateKg, 1) },
            )
        }
    }
}

@Composable
private fun StepperRow(
    label: String,
    value: String,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
) {
    val colors = RecompTheme.colors
    val type = RecompTheme.typography
    val spacing = RecompTheme.spacing
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = type.label, color = colors.dim)
        Row(
            horizontalArrangement = Arrangement.spacedBy(spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StepperButton(symbol = "-", onClick = onDecrement)
            Text(text = value, style = type.loadValue, color = colors.volt)
            StepperButton(symbol = "+", onClick = onIncrement)
        }
    }
}

@Composable
private fun StepperButton(
    symbol: String,
    onClick: () -> Unit,
) {
    val colors = RecompTheme.colors
    val spacing = RecompTheme.spacing
    Box(
        modifier =
            Modifier
                .size(STEPPER_SIZE)
                .background(colors.panelElevated, RecompTheme.shapes.sm)
                .border(spacing.border, colors.line, RecompTheme.shapes.sm)
                .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = symbol, style = RecompTheme.typography.loadValue, color = colors.txt)
    }
}

private val STEPPER_SIZE = 36.dp

/** Bar-weight stepper increment (kg). */
private const val BAR_STEP_KG = 2.5
