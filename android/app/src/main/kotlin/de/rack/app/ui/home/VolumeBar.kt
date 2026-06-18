package de.rack.app.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import de.rack.app.domain.VolumeTotals
import de.rack.app.ui.theme.RecompTheme
import de.rack.app.ui.theme.tagColor

/**
 * A single labelled bar (one chart column): its category [label] (a muscle group or a
 * plan-day tag) and its weighted [volume] (Σ weight × reps). The [color] is the bar's
 * Recomp accent — volt for muscles, the tag hue for push/pull/legs.
 */
data class VolumeBar(
    val label: String,
    val volume: Double,
    val color: Color,
)

/** Build the muscle bars in stable order, all in the single volt accent. */
@Composable
fun muscleBars(perMuscle: Map<String, VolumeTotals>): List<VolumeBar> {
    val volt = RecompTheme.colors.volt
    return perMuscle.entries.map { (muscle, totals) -> VolumeBar(muscle, totals.volume, volt) }
}

/** Build the tag bars, each colored by its plan-day tag (push/pull/legs, else volt). */
@Composable
fun tagBars(perTag: Map<String, VolumeTotals>): List<VolumeBar> {
    val colors = RecompTheme.colors
    return perTag.entries.map { (tag, totals) -> VolumeBar(tag, totals.volume, colors.tagColor(tag)) }
}
