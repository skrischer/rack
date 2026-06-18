package de.rack.app.ui.plate

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import de.rack.app.domain.PlateBreakdown
import de.rack.app.domain.PlateSlot
import de.rack.app.domain.formatPlateKg
import de.rack.app.ui.theme.RecompBadge
import de.rack.app.ui.theme.RecompBadgeStyle
import de.rack.app.ui.theme.RecompDivider
import de.rack.app.ui.theme.RecompEmpty
import de.rack.app.ui.theme.RecompTheme
import de.rack.app.ui.theme.SplineSansMonoFamily
import java.math.BigDecimal

private val DotSize = 11.dp
private val TargetFieldPaddingV = 12.dp
private val TargetValueSize = 34.sp
private val KgSuffixSize = 16.sp

/** The Recomp card surface (kit `.card`): panel fill, 1px line border, xl radius, clipped. */
@Composable
fun PlateCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = RecompTheme.colors
    val spacing = RecompTheme.spacing
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .background(colors.panel, RecompTheme.shapes.xl)
                .border(spacing.border, colors.line, RecompTheme.shapes.xl),
        content = content,
    )
}

/** A card head (kit `.card-head`): an elevated strip with a mono caption and a trailing slot. */
@Composable
fun PlateCardHead(
    label: String,
    trailing: @Composable () -> Unit = {},
) {
    val colors = RecompTheme.colors
    val spacing = RecompTheme.spacing
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(colors.panelElevated)
                .padding(horizontal = spacing.cardInsetH, vertical = spacing.lg),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = RecompTheme.typography.label, color = colors.dim)
        trailing()
    }
}

/** The target-weight card (kit target row): a large mono kg input over a dim caption, with a unit suffix. */
@Composable
fun TargetCard(
    value: String,
    onChange: (String) -> Unit,
) {
    val colors = RecompTheme.colors
    val type = RecompTheme.typography
    val spacing = RecompTheme.spacing
    PlateCard {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.cardInsetH, vertical = spacing.rowInsetV),
            horizontalArrangement = Arrangement.spacedBy(spacing.lg),
            verticalAlignment = Alignment.Bottom,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                Text(text = "ZIELGEWICHT", style = type.caption, color = colors.dim)
                TargetField(value = value, onChange = onChange)
            }
            Text(
                text = "KG",
                style = type.label.copy(fontSize = KgSuffixSize, letterSpacing = 0.12.em),
                color = colors.dim,
                modifier = Modifier.padding(bottom = TargetFieldPaddingV),
            )
        }
    }
}

@Composable
private fun TargetField(
    value: String,
    onChange: (String) -> Unit,
) {
    val colors = RecompTheme.colors
    val spacing = RecompTheme.spacing
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val borderColor = if (focused) colors.volt else colors.line
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        textStyle =
            RecompTheme.typography.loadValue.copy(
                fontFamily = SplineSansMonoFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = TargetValueSize,
                color = colors.txt,
            ),
        cursorBrush = SolidColor(colors.volt),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        interactionSource = interaction,
        modifier =
            Modifier
                .fillMaxWidth()
                .background(colors.bg, RecompTheme.shapes.sm)
                .border(spacing.border, borderColor, RecompTheme.shapes.sm)
                .padding(horizontal = spacing.md, vertical = TargetFieldPaddingV),
    )
}

/** Lays out the derived breakdown as standalone cards (barbell, per-side, equation, marker) or a state card. */
fun LazyListScope.breakdownItems(breakdown: PlateBreakdown) {
    when (breakdown) {
        is PlateBreakdown.Empty -> item { EmptyBreakdownCard() }
        is PlateBreakdown.BelowBar -> item { BelowBarCard(breakdown) }
        is PlateBreakdown.Loadable -> {
            item { BarLoadingCard(perSide = breakdown.perSide) }
            item { PerSideCard(state = breakdown) }
            item { EquationCard(state = breakdown) }
            item { StatusMarker(state = breakdown) }
        }
    }
}

@Composable
private fun EmptyBreakdownCard() {
    PlateCard { RecompEmpty(text = "Zielgewicht eingeben\num die Stange zu laden.") }
}

@Composable
private fun BelowBarCard(state: PlateBreakdown.BelowBar) {
    val colors = RecompTheme.colors
    val type = RecompTheme.typography
    val spacing = RecompTheme.spacing
    PlateCard {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.cardInsetH, vertical = spacing.rowInsetV),
            verticalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            Text(text = "UNTER STANGENGEWICHT", style = type.label, color = colors.legs)
            Text(
                text = "Ziel liegt unter der ${formatPlateKg(state.barWeight)} kg Stange — keine Scheiben.",
                style = type.body,
                color = colors.dim,
            )
        }
    }
}

@Composable
private fun BarLoadingCard(perSide: List<PlateSlot>) {
    val spacing = RecompTheme.spacing
    PlateCard {
        PlateCardHead(label = "STANGENLADUNG") { RecompBadge(text = "Symmetrisch", style = RecompBadgeStyle.Volt) }
        RecompDivider()
        Box(
            modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.cardInsetH, vertical = spacing.xxxl),
            contentAlignment = Alignment.Center,
        ) {
            BarbellDiagram(perSide = perSide)
        }
    }
}

@Composable
private fun PerSideCard(state: PlateBreakdown.Loadable) {
    val colors = RecompTheme.colors
    val type = RecompTheme.typography
    val total = perSideTotal(state.perSide)
    PlateCard {
        PlateCardHead(label = "PRO SEITE") {
            Text(text = "${formatPlateKg(total)} kg", style = type.loadValue, color = colors.volt)
        }
        if (state.perSide.isEmpty()) {
            RecompDivider()
            PerSideRowContainer { Text(text = "keine Scheiben", style = type.loadValue, color = colors.mutedEmpty) }
        } else {
            state.perSide.forEach { slot ->
                RecompDivider()
                PerSideRow(slot = slot)
            }
        }
    }
}

@Composable
private fun PerSideRow(slot: PlateSlot) {
    val colors = RecompTheme.colors
    val type = RecompTheme.typography
    val spacing = RecompTheme.spacing
    PerSideRowContainer {
        Box(modifier = Modifier.size(DotSize).background(colors.plateColor(slot.weight), CircleShape))
        Text(
            text = "${slot.count} × ${formatPlateKg(slot.weight)} kg",
            style = type.loadValue,
            color = colors.txt,
            modifier = Modifier.weight(1f).padding(start = spacing.md),
        )
        Text(
            text = formatPlateKg(slot.weight.multiply(BigDecimal(slot.count))),
            style = type.loadValue,
            color = colors.dim,
        )
    }
}

@Composable
private fun PerSideRowContainer(content: @Composable RowScope.() -> Unit) {
    val spacing = RecompTheme.spacing
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.cardInsetH, vertical = spacing.rowInsetV),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
private fun EquationCard(state: PlateBreakdown.Loadable) {
    val colors = RecompTheme.colors
    val type = RecompTheme.typography
    val spacing = RecompTheme.spacing
    val perSide = formatPlateKg(perSideTotal(state.perSide))
    PlateCard {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.cardInsetH, vertical = spacing.rowInsetV),
            horizontalArrangement = Arrangement.spacedBy(spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = "=", style = type.label, color = colors.volt)
            Text(
                text =
                    "${formatPlateKg(state.total)} kg = Stange ${formatPlateKg(state.barWeight)} + 2 × $perSide kg",
                style = type.loadValue,
                color = colors.txt,
            )
        }
    }
}

@Composable
private fun StatusMarker(state: PlateBreakdown.Loadable) {
    if (state.isExact) {
        RecompBadge(text = "Exakt · kein Rest", style = RecompBadgeStyle.Volt)
    } else {
        RecompBadge(text = "+${formatPlateKg(state.shortfall)} kg fehlen", style = RecompBadgeStyle.Legs)
    }
}

/** The per-side load: the summed weight of one end's plate stack (count × denomination). */
private fun perSideTotal(perSide: List<PlateSlot>): BigDecimal =
    perSide.fold(BigDecimal.ZERO) { acc, slot -> acc.add(slot.weight.multiply(BigDecimal(slot.count))) }
