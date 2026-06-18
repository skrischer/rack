package de.rack.app.ui.plate

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import de.rack.app.domain.PlateCalcPreferences
import de.rack.app.domain.PlateStock
import de.rack.app.domain.formatPlateKg
import de.rack.app.ui.theme.RecompDivider
import de.rack.app.ui.theme.RecompStepper
import de.rack.app.ui.theme.RecompTheme

private val DotSizePref = 11.dp

/** Bar-weight stepper increment (kg) — matches the ViewModel's bar clamp resolution. */
private const val BAR_STEP_KG = 2.5

/**
 * The persisted bar-weight + inventory editors for the plate calculator (design ref
 * docs/design/screens/plate-calc.html "Stange"). Each stepper edit is forwarded upward to
 * the ViewModel, which clamps it and persists it via the repository; this file is purely
 * presentational. Kept separate from [PlateCalcSections] so neither file overflows the
 * per-file length guideline.
 */
@Composable
fun BarSelectorRow(
    barWeightKg: Double,
    onBarWeightChange: (Double) -> Unit,
) {
    val colors = RecompTheme.colors
    val spacing = RecompTheme.spacing
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RecompTheme.shapes.xl)
                .background(colors.panel)
                .border(spacing.border, colors.line, RecompTheme.shapes.xl)
                .padding(horizontal = spacing.cardInsetH, vertical = spacing.rowInsetV),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = "STANGE", style = RecompTheme.typography.label, color = colors.txt)
        RecompStepper(
            value = "${formatPlateKg(barWeightKg.toBigDecimal())} kg",
            onDecrement = { onBarWeightChange(-BAR_STEP_KG) },
            onIncrement = { onBarWeightChange(BAR_STEP_KG) },
        )
    }
}

/** The owned-plate inventory card: one pair-count stepper per denomination, color-coded like the breakdown. */
@Composable
fun PlateInventoryCard(
    preferences: PlateCalcPreferences,
    onPairCountChange: (Double, Int) -> Unit,
) {
    PlateCard {
        PlateCardHead(label = "SCHEIBEN")
        preferences.inventory.forEach { stock ->
            RecompDivider()
            InventoryRow(stock = stock, onPairCountChange = onPairCountChange)
        }
    }
}

@Composable
private fun InventoryRow(
    stock: PlateStock,
    onPairCountChange: (Double, Int) -> Unit,
) {
    val colors = RecompTheme.colors
    val spacing = RecompTheme.spacing
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.cardInsetH, vertical = spacing.rowInsetV),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.md), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier =
                    Modifier.size(
                        DotSizePref
                    ).background(colors.plateColor(stock.plateKg.toBigDecimal()), CircleShape)
            )
            Text(
                text = "${formatPlateKg(stock.plateKg.toBigDecimal())} KG",
                style = RecompTheme.typography.label,
                color = colors.txt,
            )
        }
        RecompStepper(
            value = "${stock.pairCount}×",
            onDecrement = { onPairCountChange(stock.plateKg, -1) },
            onIncrement = { onPairCountChange(stock.plateKg, 1) },
        )
    }
}
