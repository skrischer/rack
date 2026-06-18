package de.rack.app.ui.plate

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import de.rack.app.ui.theme.RecompDivider
import de.rack.app.ui.theme.RecompTheme

/**
 * Recomp-styled plate calculator (#169): a
 * target-kg input over the derived per-side breakdown — the symmetric barbell diagram, the
 * per-side plate list, the load equation, and an exact/short marker — plus the persisted bar
 * weight and plate inventory editors. Purely presentational: it renders [state] and forwards
 * each edit / back event upward; the split is computed by the Phase-13 math behind the
 * ViewModel, so no business logic lives here. Phase 13 is kg-only.
 */
@Composable
fun PlateCalcScreen(
    state: PlateCalcUiState,
    actions: PlateCalcActions,
    modifier: Modifier = Modifier,
) {
    val colors = RecompTheme.colors
    val spacing = RecompTheme.spacing
    Column(modifier = modifier.fillMaxSize().background(colors.bg)) {
        TopBar(onBack = actions.onBack)
        RecompDivider()
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = spacing.gutter, vertical = spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.lg),
        ) {
            item { Text(text = "PLATE CALCULATOR", style = RecompTheme.typography.kicker, color = colors.volt) }
            item { TargetCard(value = state.targetInput, onChange = actions.onTargetChange) }
            breakdownItems(state.breakdown)
            item {
                BarSelectorRow(
                    barWeightKg = state.preferences.barWeightKg,
                    onBarWeightChange = actions.onBarWeightChange
                )
            }
            item { PlateInventoryCard(preferences = state.preferences, onPairCountChange = actions.onPairCountChange) }
        }
    }
}

/** The plate-calculator edit + navigation events, bundled so the screen takes one parameter. */
@Immutable
data class PlateCalcActions(
    val onBack: () -> Unit,
    val onTargetChange: (String) -> Unit,
    val onBarWeightChange: (Double) -> Unit,
    val onPairCountChange: (Double, Int) -> Unit,
)

@Composable
private fun TopBar(onBack: () -> Unit) {
    val colors = RecompTheme.colors
    val type = RecompTheme.typography
    val spacing = RecompTheme.spacing
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(colors.bg)
                .padding(horizontal = spacing.gutter, vertical = spacing.md),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = "PLATES", style = type.kicker, color = colors.volt)
        Text(
            text = "BACK",
            style = type.label,
            color = colors.dim,
            modifier =
                Modifier
                    .border(spacing.border, colors.line, RecompTheme.shapes.sm)
                    .clickable(onClick = onBack)
                    .padding(horizontal = spacing.lg, vertical = spacing.sm),
        )
    }
}
