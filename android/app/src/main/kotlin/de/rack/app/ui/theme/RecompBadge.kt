package de.rack.app.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

private val BadgePillShape = RoundedCornerShape(percent = 50)

// Per-style border/fill alphas, matching the kit's rgba() badge accents verbatim
// (kit.css `.badge.volt` .35/.06, `.badge.agent` .40/.08, `.badge.legs` border .40).
private const val VOLT_BORDER_ALPHA = 0.35f
private const val VOLT_FILL_ALPHA = 0.06f
private const val AGENT_BORDER_ALPHA = 0.40f
private const val AGENT_FILL_ALPHA = 0.08f
private const val LEGS_BORDER_ALPHA = 0.40f

/**
 * The badge accents from the Recomp kit (`.badge` + `.volt` / `.agent` / `.legs`). Each
 * resolves to a tinted pill: a colored label, a translucent same-hue border, and (for the
 * filled variants) a faint same-hue fill — every value derived from the theme tokens, no
 * ad-hoc hex.
 */
enum class RecompBadgeStyle {
    /** Neutral chip: dim label on a 1px `line` border (kit `.badge`). */
    Neutral,

    /** Volt accent — counts / "Heute" markers (kit `.badge.volt`). */
    Volt,

    /** Agent/superset violet — agent-authored markers (kit `.badge.agent`). */
    Agent,

    /** Legs orange — warning / legs-day markers (kit `.badge.legs`). */
    Legs,
}

/**
 * A small pill badge in the kit's mono caption voice (uppercased to match `text-transform`).
 * Pick a [style] for the accent; the default is the neutral outline chip.
 */
@Composable
fun RecompBadge(
    text: String,
    style: RecompBadgeStyle = RecompBadgeStyle.Neutral,
    modifier: Modifier = Modifier,
) {
    val colors = RecompTheme.colors
    val spacing = RecompTheme.spacing
    val accent = badgeAccent(style, colors)
    Text(
        text = text.uppercase(),
        style = RecompTheme.typography.caption,
        color = accent.content,
        modifier =
            modifier
                .background(accent.fill, BadgePillShape)
                .border(spacing.border, accent.border, BadgePillShape)
                .padding(horizontal = spacing.sm, vertical = spacing.xxs),
    )
}

private data class BadgeAccent(
    val content: Color,
    val border: Color,
    val fill: Color,
)

private fun badgeAccent(
    style: RecompBadgeStyle,
    colors: RecompColors,
): BadgeAccent =
    when (style) {
        RecompBadgeStyle.Neutral ->
            BadgeAccent(colors.dim, colors.line, Color.Transparent)
        RecompBadgeStyle.Volt ->
            BadgeAccent(
                colors.volt,
                colors.volt.copy(alpha = VOLT_BORDER_ALPHA),
                colors.volt.copy(alpha = VOLT_FILL_ALPHA)
            )
        RecompBadgeStyle.Agent ->
            BadgeAccent(
                colors.superset,
                colors.superset.copy(alpha = AGENT_BORDER_ALPHA),
                colors.superset.copy(alpha = AGENT_FILL_ALPHA),
            )
        RecompBadgeStyle.Legs ->
            BadgeAccent(colors.legs, colors.legs.copy(alpha = LEGS_BORDER_ALPHA), Color.Transparent)
    }
