package de.rack.app.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Vector icons for the bottom-nav dock and its off-canvas overflow, traced verbatim from the
 * stroked SVG paths in the Recomp nav-dock design. They carry no intrinsic color — the stroke
 * is a placeholder recolored by the consuming [androidx.compose.material3.Icon]'s tint
 * (`LocalContentColor`), so the dock's volt/dim active state and the overflow's dim leading icons
 * resolve through the Recomp theme like every other surface.
 */
object RackNavIcons {
    /** Barbell (kit `.bottomnav` Plan tile). */
    val Plan: ImageVector by lazy { navIcon("M4 9v6M7 7v10M17 7v10M20 9v6M7 12h10") }

    /** Calendar (kit Verlauf tile). */
    val Calendar: ImageVector by lazy {
        navIcon(
            "M5.5 5 h13 a2 2 0 0 1 2 2 v11 a2 2 0 0 1 -2 2 h-13 a2 2 0 0 1 -2 -2 v-11 " +
                "a2 2 0 0 1 2 -2 z M3.5 9.5 h17 M8 3.5 v3 M16 3.5 v3",
        )
    }

    /** Ascending bars (kit Statistik tile). */
    val Stats: ImageVector by lazy { navIcon("M5 20V11M12 20V4M19 20v-6", strokeWidth = STATS_STROKE) }

    /** Sparkle (kit Artifacts tile). */
    val Artifacts: ImageVector by lazy {
        navIcon(
            "M12 3l1.7 5.1a3 3 0 0 0 2.2 2.2L21 12l-5.1 1.7a3 3 0 0 0-2.2 2.2L12 21l-1.7-5.1" +
                "a3 3 0 0 0-2.2-2.2L3 12l5.1-1.7a3 3 0 0 0 2.2-2.2z",
            strokeWidth = ARTIFACTS_STROKE,
        )
    }

    /** Key + magnifier (kit overflow API-Keys row). */
    val Key: ImageVector by lazy {
        navIcon("M4.5 9 a4.5 4.5 0 1 0 9 0 a4.5 4.5 0 1 0 -9 0 z M12.5 12.5 L20 20 M17 17 l2 -2")
    }

    /** Sliders (kit overflow Einstellungen row). */
    val Settings: ImageVector by lazy {
        navIcon(
            "M4 7h9M19 7h1M4 12h1M11 12h9M4 17h6M16 17h4 " +
                "M14 7 a2 2 0 1 0 4 0 a2 2 0 1 0 -4 0 z " +
                "M6 12 a2 2 0 1 0 4 0 a2 2 0 1 0 -4 0 z " +
                "M11 17 a2 2 0 1 0 4 0 a2 2 0 1 0 -4 0 z",
        )
    }

    /** Door + arrow (kit overflow Abmelden row). */
    val Logout: ImageVector by lazy {
        navIcon("M9 5H5a1 1 0 0 0 -1 1v12a1 1 0 0 0 1 1h4M16 8l4 4-4 4M20 12H9")
    }
}

private const val ICON_VIEWPORT = 24f
private const val DEFAULT_STROKE = 2f
private const val STATS_STROKE = 2.2f
private const val ARTIFACTS_STROKE = 1.8f
private val IconSize = ICON_VIEWPORT.dp

/**
 * Builds a 24dp stroked [ImageVector] from an SVG path string. The stroke uses an arbitrary opaque
 * color; the rendering [androidx.compose.material3.Icon] overrides it with its tint, so the icon
 * always paints in the current content color.
 */
private fun navIcon(
    pathData: String,
    strokeWidth: Float = DEFAULT_STROKE,
): ImageVector =
    ImageVector.Builder(
        defaultWidth = IconSize,
        defaultHeight = IconSize,
        viewportWidth = ICON_VIEWPORT,
        viewportHeight = ICON_VIEWPORT,
    ).addPath(
        pathData = addPathNodes(pathData),
        stroke = SolidColor(Color.Black),
        strokeLineWidth = strokeWidth,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    ).build()
