package de.rack.app.ui.exercise

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import de.rack.app.domain.ExerciseProgressPoint
import de.rack.app.ui.theme.RecompTheme
import java.time.format.DateTimeFormatter

/*
 * Presentational sections for the per-exercise progress screen (spec-dashboards.md,
 * Phase 11): the two-series chart legend and the tabular fallback that lists the same
 * (date, top-set weight, session volume) rows the chart plots, in kg with no unit
 * toggle and no estimated-1RM metric. Split from ExerciseProgressScreen.kt to keep
 * each file within the function-count guideline. No business logic here — only render.
 */

private val ROW_DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")

/** Legend mapping the two chart series to their metric and Recomp color. */
@Composable
internal fun ChartLegend() {
    val colors = RecompTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(RecompTheme.spacing.lg)) {
        LegendItem(label = "TOP-SET KG", swatch = colors.volt)
        LegendItem(label = "SESSION VOLUME", swatch = colors.pull)
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
        Box(modifier = Modifier.size(spacing.md).background(swatch, RecompTheme.shapes.sm))
        Text(text = label, style = RecompTheme.typography.caption, color = RecompTheme.colors.dim)
    }
}

/** Column headers for the table fallback. */
@Composable
internal fun ProgressTableHeader() {
    val colors = RecompTheme.colors
    val caption = RecompTheme.typography.caption
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = RecompTheme.spacing.xs),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Cell(text = "DATE", style = caption, color = colors.dim, align = TextAlign.Start)
        Cell(text = "TOP-SET", style = caption, color = colors.dim, align = TextAlign.End)
        Cell(text = "VOLUME", style = caption, color = colors.dim, align = TextAlign.End)
    }
}

/** One (date, top-set weight, session volume) row of the table fallback. */
@Composable
internal fun ProgressTableRow(point: ExerciseProgressPoint) {
    val colors = RecompTheme.colors
    val type = RecompTheme.typography
    val spacing = RecompTheme.spacing
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(colors.panel, RecompTheme.shapes.sm)
                .padding(horizontal = spacing.md, vertical = spacing.sm),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        val date = point.date.format(ROW_DATE_FORMAT)
        Cell(text = date, style = type.history, color = colors.txt, align = TextAlign.Start)
        Cell(text = topSetText(point), style = type.loadValue, color = colors.volt, align = TextAlign.End)
        Cell(text = volumeText(point), style = type.loadValue, color = colors.pull, align = TextAlign.End)
    }
}

@Composable
private fun RowScope.Cell(
    text: String,
    style: TextStyle,
    color: Color,
    align: TextAlign,
) {
    Text(text = text, style = style, color = color, textAlign = align, modifier = Modifier.weight(1f))
}

private fun topSetText(point: ExerciseProgressPoint): String =
    point.topSetWeight?.let { weight -> "${formatNumber(weight)} kg" } ?: "—"

private fun volumeText(point: ExerciseProgressPoint): String = "${formatNumber(point.volume)} kg"

/** Drop a trailing ".0" so whole kg values read cleanly in the mono table. */
private fun formatNumber(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
