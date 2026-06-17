package de.rack.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * The Recomp dark palette, encoding docs/design-tokens.md exactly. These are the
 * raw tokens from artifact.html's `:root`; semantic roles map onto them (accent is
 * [volt], on-volt content is [bg], surfaces step bg < panel < panelElevated).
 *
 * This is a bespoke dark theme, not Material 3 dynamic color. The palette is
 * exposed through [LocalRecompColors] and consumed via `RecompTheme.colors`.
 */
@Immutable
data class RecompColors(
    val bg: Color,
    val panel: Color,
    val panelElevated: Color,
    val line: Color,
    val txt: Color,
    val dim: Color,
    val volt: Color,
    val voltDim: Color,
    val mutedEmpty: Color,
    val push: Color,
    val pull: Color,
    val legs: Color,
    val superset: Color,
    val supersetTint: Color,
    val warningBg: Color,
    val warningText: Color,
)

/** The single Recomp dark palette (no light variant in the prototype yet). */
val recompDarkColors: RecompColors =
    RecompColors(
        bg = Color(0xFF0D0E11),
        panel = Color(0xFF15171C),
        panelElevated = Color(0xFF1B1E24),
        line = Color(0xFF2A2E37),
        txt = Color(0xFFE8EAED),
        dim = Color(0xFF8B909B),
        volt = Color(0xFFC8F23A),
        voltDim = Color(0xFF7E9120),
        mutedEmpty = Color(0xFF5A5F6A),
        push = Color(0xFFC8F23A),
        pull = Color(0xFF54C7EC),
        legs = Color(0xFFFF8A5B),
        superset = Color(0xFFA78BFA),
        // Superset group background: --ss at 5% (rgba(167, 139, 250, 0.05)).
        supersetTint = Color(0xFFA78BFA).copy(alpha = 0.05f),
        // Warning band: background #33231a, border legs, text #ffc6a3.
        warningBg = Color(0xFF33231A),
        warningText = Color(0xFFFFC6A3),
    )
