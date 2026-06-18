package de.rack.app.ui.exercise

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import de.rack.app.domain.ExerciseProgressPoint
import de.rack.app.ui.theme.RecompDivider
import de.rack.app.ui.theme.RecompTheme
import java.time.format.DateTimeFormatter

/*
 * Presentational sections for the per-exercise progress screen (spec-dashboards.md,
 * Phase 11): the chart card (Vico two-series chart + legend) and the tabular fallback
 * card that lists the same (date, top-set weight, session volume) rows the chart plots,
 * in kg with no unit toggle and no estimated-1RM metric. Split from ExerciseProgressScreen.kt
 * to keep each file within the function-count guideline. No business logic here — only render.
 */

private val ROW_DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")

/** The Recomp card surface (kit `.card`): panel fill, 1px line border, xl radius, clipped. */
@Composable
private fun ProgressCard(content: @Composable () -> Unit) {
    val colors = RecompTheme.colors
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RecompTheme.shapes.xl)
                .background(colors.panel)
                .border(RecompTheme.spacing.border, colors.line, RecompTheme.shapes.xl),
    ) {
        content()
    }
}

/** A card head (kit `.card-head`): an elevated strip with a mono metric caption. */
@Composable
private fun ProgressCardHead(label: String) {
    val colors = RecompTheme.colors
    val spacing = RecompTheme.spacing
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(colors.panelElevated)
                .padding(horizontal = spacing.cardInsetH, vertical = spacing.lg),
    ) {
        Text(text = label, style = RecompTheme.typography.label, color = colors.dim)
    }
}

/** The chart card: a metric caption head over the Vico chart and its two-series legend. */
@Composable
internal fun ProgressChartCard(points: List<ExerciseProgressPoint>) {
    val spacing = RecompTheme.spacing
    ProgressCard {
        ProgressCardHead(label = "TOP-SATZ · VOLUMEN · KG")
        RecompDivider()
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.cardInsetH, vertical = spacing.lg),
        ) {
            ExerciseProgressChart(points = points)
        }
        RecompDivider()
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.cardInsetH, vertical = spacing.md),
        ) {
            ChartLegend()
        }
    }
}

/** The table card: a header row over hairline-divided (date, top-set, volume) rows. */
@Composable
internal fun ProgressTableCard(points: List<ExerciseProgressPoint>) {
    ProgressCard {
        ProgressTableHeader()
        points.forEach { point ->
            RecompDivider()
            ProgressTableRow(point = point)
        }
    }
}

/** Legend mapping the two chart series to their metric and Recomp color. */
@Composable
private fun ChartLegend() {
    val colors = RecompTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(RecompTheme.spacing.lg)) {
        LegendItem(label = "TOP-SATZ", swatch = colors.volt)
        LegendItem(label = "VOLUMEN", swatch = colors.pull)
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
private fun ProgressTableHeader() {
    val colors = RecompTheme.colors
    val caption = RecompTheme.typography.caption
    val spacing = RecompTheme.spacing
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.cardInsetH, vertical = spacing.md),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Cell(text = "DATUM", style = caption, color = colors.dim, align = TextAlign.Start)
        Cell(text = "TOP-SATZ", style = caption, color = colors.dim, align = TextAlign.End)
        Cell(text = "VOLUMEN", style = caption, color = colors.dim, align = TextAlign.End)
    }
}

/** One (date, top-set weight, session volume) row of the table fallback. */
@Composable
private fun ProgressTableRow(point: ExerciseProgressPoint) {
    val colors = RecompTheme.colors
    val type = RecompTheme.typography
    val spacing = RecompTheme.spacing
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.cardInsetH, vertical = spacing.rowInsetV),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        val date = point.date.format(ROW_DATE_FORMAT)
        Cell(text = date, style = type.history, color = colors.txt, align = TextAlign.Start)
        Cell(text = topSetText(point), style = type.loadValue, color = colors.txt, align = TextAlign.End)
        Cell(text = volumeText(point), style = type.loadValue, color = colors.dim, align = TextAlign.End)
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
