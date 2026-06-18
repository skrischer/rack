package de.rack.app.ui.theme

import android.graphics.Typeface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import androidx.core.content.res.ResourcesCompat
import com.patrykandpatrick.vico.compose.common.ProvideVicoTheme
import com.patrykandpatrick.vico.compose.common.VicoTheme
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent
import com.patrykandpatrick.vico.compose.m3.common.rememberM3VicoTheme
import com.patrykandpatrick.vico.core.common.component.TextComponent
import de.rack.app.R

// Recomp-themed Vico charting (docs/design-tokens.md, spec-dashboards.md). A thin wrapper
// that styles Vico's VicoTheme in the bespoke dark theme: chart series use the
// volt/push/pull/legs/superset palette, axis lines/labels use the Recomp line/dim tokens,
// and axis labels render in Spline Sans Mono. Phase 11 chart screens consume this so every
// chart matches the prototype without restyling individually.

/**
 * The default series-color order for categorical Recomp charts: the volt accent first, then
 * the pull/legs/superset tag hues. Mirrors the prototype's push==volt color coding so a
 * push/pull/legs breakdown maps onto these in order.
 */
@Composable
@ReadOnlyComposable
private fun recompChartSeriesColors(colors: RecompColors): List<Color> =
    listOf(colors.volt, colors.pull, colors.legs, colors.superset)

/**
 * Builds and remembers a [VicoTheme] from the Recomp palette. Starts from the Material 3
 * theme Vico derives (so any chart element this wrapper does not name still reads on-brand),
 * then overrides the series, axis-line, and axis-label colors with the Recomp tokens.
 */
@Composable
fun rememberRecompVicoTheme(): VicoTheme {
    val colors = RecompTheme.colors
    val seriesColors = recompChartSeriesColors(colors)
    return rememberM3VicoTheme(
        columnCartesianLayerColors = seriesColors,
        lineCartesianLayerColors = seriesColors,
        lineColor = colors.line,
        textColor = colors.dim,
    )
}

/**
 * Provides the Recomp [VicoTheme] to chart content. Wrap any Vico `CartesianChartHost` in
 * this so its layers and axes pick up the dark theme + volt/tag palette by default.
 */
@Composable
fun RecompChartTheme(content: @Composable () -> Unit) {
    ProvideVicoTheme(rememberRecompVicoTheme(), content)
}

/** Axis label size — the Recomp 10px mono caption, expressed unscaled for Vico's renderer. */
private const val AXIS_LABEL_SIZE_SP = 10f

/**
 * A Vico axis-label [TextComponent] in Spline Sans Mono — the Recomp "instrument panel" voice
 * for numeric/label text. Pass to a `HorizontalAxis`/`VerticalAxis` `label` so chart axes match
 * the mono captions used elsewhere; Vico styles axis labels via a component, not the [VicoTheme].
 */
@Composable
fun rememberRecompAxisLabel(): TextComponent {
    val context = LocalContext.current
    val monoTypeface =
        remember(context) {
            ResourcesCompat.getFont(context, R.font.spline_sans_mono_medium) ?: Typeface.MONOSPACE
        }
    return rememberTextComponent(
        color = RecompTheme.colors.dim,
        typeface = monoTypeface,
        textSize = AXIS_LABEL_SIZE_SP.sp,
    )
}
