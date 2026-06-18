package de.rack.app.ui.exercise

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.common.fill
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import com.patrykandpatrick.vico.core.cartesian.layer.LineCartesianLayer
import de.rack.app.domain.ExerciseProgressPoint
import de.rack.app.ui.theme.RecompChartTheme
import de.rack.app.ui.theme.RecompTheme
import de.rack.app.ui.theme.rememberRecompAxisLabel
import java.time.format.DateTimeFormatter

// The per-exercise progress Vico line chart (spec-dashboards.md, Phase 11). Plots two
// series over the logged dates — top-set weight (volt) and per-session volume (pull) —
// styled through the Recomp chart theme. The x-axis maps each logged date to its index;
// labels show the date so the trend reads left to right. The caller guarantees at least
// two points, so the series is never empty here.

private const val CHART_HEIGHT_DP = 240
private val AXIS_DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM")

@Composable
internal fun ExerciseProgressChart(
    points: List<ExerciseProgressPoint>,
    modifier: Modifier = Modifier,
) {
    val colors = RecompTheme.colors
    val modelProducer = remember { CartesianChartModelProducer() }
    LaunchedEffect(points) {
        modelProducer.runTransaction {
            lineSeries {
                series(points.indices.toList(), points.map { it.topSetWeight ?: 0.0 })
                series(points.indices.toList(), points.map { it.volume })
            }
        }
    }
    val dateLabels = remember(points) { points.map { it.date.format(AXIS_DATE_FORMAT) } }
    RecompChartTheme {
        CartesianChartHost(
            chart =
                rememberCartesianChart(
                    rememberLineCartesianLayer(
                        lineProvider =
                            LineCartesianLayer.LineProvider.series(
                                LineCartesianLayer.rememberLine(
                                    fill = LineCartesianLayer.LineFill.single(fill(colors.volt)),
                                ),
                                LineCartesianLayer.rememberLine(
                                    fill = LineCartesianLayer.LineFill.single(fill(colors.pull)),
                                ),
                            ),
                    ),
                    startAxis = VerticalAxis.rememberStart(label = rememberRecompAxisLabel()),
                    bottomAxis =
                        HorizontalAxis.rememberBottom(
                            label = rememberRecompAxisLabel(),
                            valueFormatter = dateAxisFormatter(dateLabels),
                        ),
                ),
            modelProducer = modelProducer,
            modifier = modifier.fillMaxWidth().height(CHART_HEIGHT_DP.dp),
        )
    }
}

/** Maps each integer x value (the point index) back to its formatted logged date. */
@Composable
private fun dateAxisFormatter(labels: List<String>): CartesianValueFormatter =
    remember(labels) {
        CartesianValueFormatter { _, value, _ -> labels.getOrNull(value.toInt()).orEmpty() }
    }
