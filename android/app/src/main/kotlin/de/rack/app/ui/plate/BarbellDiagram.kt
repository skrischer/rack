package de.rack.app.ui.plate

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import de.rack.app.domain.PlateSlot
import de.rack.app.ui.theme.RecompTheme
import java.math.BigDecimal

private val DiagramHeight = 96.dp
private val CollarWidth = 14.dp
private val CollarHeight = 8.dp
private val BarHeight = 6.dp
private val BarMinWidth = 34.dp
private val PlateGap = 1.dp
private val PlateMinHeight = 28.dp
private val PlateMaxHeight = 92.dp
private val PlateMinWidth = 7.dp
private val PlateMaxWidth = 13.dp
private val PlateShape = RoundedCornerShape(3.dp)
private val CollarShape = RoundedCornerShape(2.dp)

// The largest canonical plate (25 kg) anchors the full plate height/width; lighter plates
// scale down linearly. A defensive cap keeps a pathologically deep stack from overflowing.
private const val REFERENCE_PLATE_KG = 25.0
private const val MAX_PLATES_PER_SIDE = 12

// Clamp the scale fraction so the lightest plate still reads as a visible chip (never zero-height)
// and nothing exceeds the 25 kg reference size.
private const val MIN_PLATE_FRACTION = 0.1f
private const val MAX_PLATE_FRACTION = 1f

/**
 * The symmetric barbell illustration (design ref `plate-calc.html` "Stangenladung"): collar
 * ends flank the per-side plate stacks — largest plate outermost — around the flexible bar.
 * Plate height and width scale with the denomination and the fill comes from [plateColor]; the
 * stack is derived purely from [perSide] (largest-first) so it mirrors the computed split.
 */
@Composable
fun BarbellDiagram(
    perSide: List<PlateSlot>,
    modifier: Modifier = Modifier,
) {
    val colors = RecompTheme.colors
    val plates = perSide.flatMap { slot -> List(slot.count) { slot.weight } }.take(MAX_PLATES_PER_SIDE)
    Row(
        modifier = modifier.fillMaxWidth().height(DiagramHeight),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Collar()
        plates.forEach { weight -> Plate(weight) }
        Bar()
        plates.asReversed().forEach { weight -> Plate(weight) }
        Collar()
    }
}

@Composable
private fun Collar() {
    Box(
        modifier =
            Modifier
                .width(CollarWidth)
                .height(CollarHeight)
                .background(RecompTheme.colors.line, CollarShape),
    )
}

@Composable
private fun RowScope.Bar() {
    Box(
        modifier =
            Modifier
                .weight(1f)
                .widthIn(min = BarMinWidth)
                .height(BarHeight)
                .background(RecompTheme.colors.line, PlateShape),
    )
}

@Composable
private fun Plate(weight: BigDecimal) {
    val colors = RecompTheme.colors
    Box(
        modifier =
            Modifier
                .padding(horizontal = PlateGap)
                .width(plateDimension(weight, PlateMinWidth, PlateMaxWidth))
                .height(plateDimension(weight, PlateMinHeight, PlateMaxHeight))
                .background(colors.plateColor(weight), PlateShape),
    )
}

/** Linearly interpolates a plate dimension between [min] and [max] by its share of the 25 kg reference. */
private fun plateDimension(
    weight: BigDecimal,
    min: Dp,
    max: Dp,
): Dp {
    val fraction = (weight.toDouble() / REFERENCE_PLATE_KG).toFloat().coerceIn(MIN_PLATE_FRACTION, MAX_PLATE_FRACTION)
    return lerp(min, max, fraction)
}
