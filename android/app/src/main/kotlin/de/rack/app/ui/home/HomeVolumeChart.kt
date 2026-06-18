package de.rack.app.ui.home

import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import com.patrykandpatrick.vico.compose.common.fill
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.core.cartesian.data.ColumnCartesianLayerModel
import com.patrykandpatrick.vico.core.cartesian.data.columnSeries
import com.patrykandpatrick.vico.core.cartesian.layer.ColumnCartesianLayer
import com.patrykandpatrick.vico.core.common.component.LineComponent
import com.patrykandpatrick.vico.core.common.data.ExtraStore
import de.rack.app.ui.theme.RecompChartTheme
import de.rack.app.ui.theme.rememberRecompAxisLabel

/** Bar height for the weekly-volume charts. */
private val CHART_HEIGHT = 180.dp

/** Column thickness; bars stay legible without the chart needing horizontal scroll. */
private val COLUMN_THICKNESS = 18.dp

/**
 * A Recomp-themed Vico column chart of [bars], one column per category in order, each
 * colored by its [VolumeBar.color]. The X axis labels the categories (mono caption) and
 * the Y axis shows weighted volume. The caller guarantees [bars] is non-empty (the
 * screen renders an empty state otherwise) so Vico is never handed an empty series.
 */
@Composable
fun VolumeColumnChart(
    bars: List<VolumeBar>,
    modifier: Modifier = Modifier,
) {
    val modelProducer = remember { CartesianChartModelProducer() }
    LoadVolumeSeries(modelProducer, bars)
    val columns = bars.map { bar -> rememberLineComponent(fill = fill(bar.color), thickness = COLUMN_THICKNESS) }
    val provider = remember(columns) { perBarColumnProvider(columns) }
    val labelFormatter = rememberCategoryFormatter(bars.map { it.label })
    RecompChartTheme {
        CartesianChartHost(
            chart =
                rememberCartesianChart(
                    rememberColumnCartesianLayer(columnProvider = provider),
                    startAxis = VerticalAxis.rememberStart(label = rememberRecompAxisLabel()),
                    bottomAxis =
                        HorizontalAxis.rememberBottom(
                            label = rememberRecompAxisLabel(),
                            valueFormatter = labelFormatter,
                        ),
                ),
            modelProducer = modelProducer,
            modifier = modifier.height(CHART_HEIGHT),
            scrollState = rememberVicoScrollState(scrollEnabled = false),
        )
    }
}

@Composable
private fun LoadVolumeSeries(
    modelProducer: CartesianChartModelProducer,
    bars: List<VolumeBar>,
) {
    val values = bars.map { it.volume }
    LaunchedEffect(values) {
        modelProducer.runTransaction {
            columnSeries { series(values) }
        }
    }
}

/**
 * A [CartesianValueFormatter] that maps a column's integer x index to its category
 * [labels] entry; out-of-range indices render blank. Lets the bottom axis show the
 * muscle/tag names rather than the raw indices.
 */
@Composable
private fun rememberCategoryFormatter(labels: List<String>): CartesianValueFormatter =
    remember(labels) {
        CartesianValueFormatter { _, value, _ ->
            labels.getOrNull(value.toInt())?.uppercase().orEmpty()
        }
    }

/**
 * A [ColumnCartesianLayer.ColumnProvider] that colors each column by its x position so a
 * single series can carry per-category Recomp colors (volt for muscles, tag hues for
 * push/pull/legs). [columns] is index-aligned to the bars.
 */
private fun perBarColumnProvider(columns: List<LineComponent>): ColumnCartesianLayer.ColumnProvider =
    object : ColumnCartesianLayer.ColumnProvider {
        override fun getColumn(
            entry: ColumnCartesianLayerModel.Entry,
            seriesIndex: Int,
            extraStore: ExtraStore,
        ): LineComponent = columns[entry.x.toInt().coerceIn(0, columns.lastIndex)]

        override fun getWidestSeriesColumn(
            seriesIndex: Int,
            extraStore: ExtraStore,
        ): LineComponent = columns.first()
    }
