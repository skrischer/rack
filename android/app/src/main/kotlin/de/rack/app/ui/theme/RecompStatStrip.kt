package de.rack.app.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp

// Kit `.statstrip .s b` value size (19px mono) — the "instrument panel" metric voice.
private val StatValueSize = 19.sp

/** One metric in a [RecompStatStrip]: a large mono [value] over an uppercase mono [label]. */
data class RecompStat(
    val value: String,
    val label: String,
)

/**
 * The session/summary metric strip from the kit (`.statstrip`): a single bordered, rounded
 * panel splitting evenly into the supplied [stats], each cell volt-valued over a dim caption,
 * with 1px `line` dividers between. Used for the live-session header (Dauer / Volumen / Sätze)
 * and summary headers.
 */
@Composable
fun RecompStatStrip(
    stats: List<RecompStat>,
    modifier: Modifier = Modifier,
) {
    val colors = RecompTheme.colors
    val spacing = RecompTheme.spacing
    Row(
        modifier =
            modifier
                .height(IntrinsicSize.Min)
                .clip(RecompTheme.shapes.xl)
                .background(colors.panel)
                .border(spacing.border, colors.line, RecompTheme.shapes.xl),
    ) {
        stats.forEachIndexed { index, stat ->
            if (index > 0) {
                Box(
                    modifier =
                        Modifier
                            .width(spacing.border)
                            .fillMaxHeight()
                            .background(colors.line),
                )
            }
            StatCell(stat = stat, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun StatCell(
    stat: RecompStat,
    modifier: Modifier = Modifier,
) {
    val colors = RecompTheme.colors
    val type = RecompTheme.typography
    val spacing = RecompTheme.spacing
    Column(
        modifier = modifier.padding(horizontal = spacing.sm, vertical = spacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(spacing.xxs),
    ) {
        Text(
            text = stat.value,
            style = type.loadValue.copy(fontSize = StatValueSize),
            color = colors.volt,
            textAlign = TextAlign.Center,
        )
        Text(text = stat.label.uppercase(), style = type.caption, color = colors.dim, textAlign = TextAlign.Center)
    }
}
