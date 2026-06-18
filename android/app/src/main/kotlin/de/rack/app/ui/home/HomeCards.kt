package de.rack.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import de.rack.app.ui.theme.RecompTheme

/*
 * Screen-local Recomp card primitives for the Home overview (docs/design/screens/home.html):
 * the panel card surface, its elevated head with a trailing badge slot, a labelled chart
 * block, and the push/pull/legs/superset chart legend. Purely presentational — every color
 * resolves through the Recomp theme; no business logic or data access here.
 */

/** The Recomp card surface (kit `.card`): panel fill, 1px line border, xl radius, clipped. */
@Composable
internal fun HomeCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = RecompTheme.colors
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RecompTheme.shapes.xl)
                .background(colors.panel)
                .border(RecompTheme.spacing.border, colors.line, RecompTheme.shapes.xl),
        content = content,
    )
}

/** A card head (kit `.card-head`): an elevated strip with a mono caption and a trailing slot. */
@Composable
internal fun HomeCardHead(
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
        Text(text = label.uppercase(), style = RecompTheme.typography.label, color = colors.volt)
        trailing()
    }
}

/** A labelled chart block inside a card: a dim mono caption over its [content] chart. */
@Composable
internal fun HomeChartBlock(
    caption: String,
    content: @Composable () -> Unit,
) {
    val spacing = RecompTheme.spacing
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.cardInsetH, vertical = spacing.lg),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        Text(text = caption.uppercase(), style = RecompTheme.typography.caption, color = RecompTheme.colors.dim)
        content()
    }
}

/** The chart legend (kit `.legend`): the four tag swatches over their dim mono labels. */
@Composable
internal fun TagLegend() {
    val colors = RecompTheme.colors
    val spacing = RecompTheme.spacing
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.cardInsetH, vertical = spacing.md),
        horizontalArrangement = Arrangement.spacedBy(spacing.lg),
    ) {
        LegendItem(label = "Push", swatch = colors.push)
        LegendItem(label = "Pull", swatch = colors.pull)
        LegendItem(label = "Legs", swatch = colors.legs)
        LegendItem(label = "Superset", swatch = colors.superset)
    }
}

@Composable
private fun LegendItem(
    label: String,
    swatch: Color,
) {
    val spacing = RecompTheme.spacing
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        Box(modifier = Modifier.size(spacing.md).clip(RecompTheme.shapes.sm).background(swatch))
        Text(text = label, style = RecompTheme.typography.caption, color = RecompTheme.colors.dim)
    }
}
