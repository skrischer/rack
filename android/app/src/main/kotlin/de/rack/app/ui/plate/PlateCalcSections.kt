package de.rack.app.ui.plate

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import de.rack.app.domain.PlateBreakdown
import de.rack.app.domain.PlateSlot
import de.rack.app.domain.formatPlateKg
import de.rack.app.ui.theme.RecompTheme
import java.math.BigDecimal

/** A panel-styled section with a volt kicker title over its [content] (matches Settings). */
@Composable
fun PlateSection(
    title: String,
    content: @Composable () -> Unit,
) {
    val colors = RecompTheme.colors
    val spacing = RecompTheme.spacing
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(colors.panel, RecompTheme.shapes.xl)
                .border(spacing.border, colors.line, RecompTheme.shapes.xl)
                .padding(spacing.cardInsetH),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        Text(text = title, style = RecompTheme.typography.kicker, color = colors.volt)
        content()
    }
}

/** The editable target-weight input (kg), styled like the logging field. */
@Composable
fun TargetInput(
    value: String,
    onChange: (String) -> Unit,
) {
    val colors = RecompTheme.colors
    val type = RecompTheme.typography
    val spacing = RecompTheme.spacing
    Column(verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
        Text(text = "TARGET KG", style = type.caption, color = colors.dim)
        TextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = LocalTextStyle.current.merge(type.intro.copy(color = colors.txt)),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            colors = fieldColors(),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Render the derived breakdown: the per-side stack and total, or the below-bar / empty state. */
@Composable
fun BreakdownSection(breakdown: PlateBreakdown) {
    PlateSection(title = "BREAKDOWN") {
        when (breakdown) {
            is PlateBreakdown.Empty -> EmptyState()
            is PlateBreakdown.BelowBar -> BelowBarState(breakdown)
            is PlateBreakdown.Loadable -> LoadableState(breakdown)
        }
    }
}

@Composable
private fun EmptyState() {
    Text(
        text = "Enter a target weight to load.",
        style = RecompTheme.typography.body,
        color = RecompTheme.colors.mutedEmpty,
    )
}

@Composable
private fun BelowBarState(state: PlateBreakdown.BelowBar) {
    val colors = RecompTheme.colors
    val type = RecompTheme.typography
    Text(text = "BELOW BAR WEIGHT", style = type.label, color = colors.legs)
    Text(
        text = "Target is under the ${formatPlateKg(state.barWeight)} kg bar — no plates.",
        style = type.body,
        color = colors.dim,
    )
}

@Composable
private fun LoadableState(state: PlateBreakdown.Loadable) {
    val colors = RecompTheme.colors
    val type = RecompTheme.typography
    val spacing = RecompTheme.spacing
    PerSideList(barWeight = state.barWeight, perSide = state.perSide)
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text = "TOTAL", style = type.label, color = colors.dim)
        Text(text = "${formatPlateKg(state.total)} kg", style = type.loadValue, color = colors.volt)
    }
    if (!state.isExact) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(colors.warningBg, RecompTheme.shapes.sm)
                    .border(spacing.border, colors.legs, RecompTheme.shapes.sm)
                    .padding(horizontal = spacing.md, vertical = spacing.sm),
        ) {
            Text(
                text = "+${formatPlateKg(state.shortfall)} kg short",
                style = type.label,
                color = colors.warningText,
            )
        }
    }
}

@Composable
private fun PerSideList(
    barWeight: BigDecimal,
    perSide: List<PlateSlot>,
) {
    val colors = RecompTheme.colors
    val type = RecompTheme.typography
    val spacing = RecompTheme.spacing
    Text(text = "PER SIDE", style = type.caption, color = colors.dim)
    Text(text = "bar ${formatPlateKg(barWeight)}", style = type.loadValue, color = colors.txt)
    if (perSide.isEmpty()) {
        Text(text = "no plates", style = type.history, color = colors.mutedEmpty)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
        perSide.forEach { slot ->
            Text(
                text = "${slot.count} × ${formatPlateKg(slot.weight)} kg",
                style = type.loadValue,
                color = colors.txt,
            )
        }
    }
}

@Composable
private fun fieldColors() =
    TextFieldDefaults.colors(
        focusedContainerColor = RecompTheme.colors.bg,
        unfocusedContainerColor = RecompTheme.colors.bg,
        disabledContainerColor = RecompTheme.colors.bg,
        focusedIndicatorColor = RecompTheme.colors.volt,
        unfocusedIndicatorColor = RecompTheme.colors.line,
        cursorColor = RecompTheme.colors.volt,
    )
