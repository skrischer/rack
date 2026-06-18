package de.rack.app.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextAlign

// Dimming applied to a disabled button so its inert state reads visually (Material disabled alpha).
private const val DISABLED_ALPHA = 0.38f

/**
 * The primary Recomp call-to-action (kit `.btn`): a solid volt fill with [RecompColors.bg]
 * text in the mono label voice, `md` radius, and the shared press-scale feedback. Set
 * [fillMaxWidth] for the block variant (`.btn-block`, e.g. the "Session starten" CTA).
 *
 * Presentational only — the label is uppercased to match the kit; logic stays in callers.
 */
@Composable
fun RecompPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    fillMaxWidth: Boolean = false,
) {
    val colors = RecompTheme.colors
    val type = RecompTheme.typography
    val spacing = RecompTheme.spacing
    val width = if (fillMaxWidth) Modifier.fillMaxWidth() else Modifier
    Box(
        modifier =
            modifier
                .then(width)
                .alpha(if (enabled) 1f else DISABLED_ALPHA)
                .recompPress(onClick = onClick, enabled = enabled)
                .background(colors.volt, RecompTheme.shapes.md)
                .padding(horizontal = spacing.lg, vertical = spacing.md),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text.uppercase(),
            style = type.label,
            color = colors.bg,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * The secondary Recomp action (kit `.btn-ghost`): transparent fill, 1px `line` border, dim
 * mono label, `sm` radius. Used for retry / low-emphasis actions (e.g. the error-state
 * "Erneut versuchen", or the rest-timer ±15 / Skip controls).
 */
@Composable
fun RecompGhostButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = RecompTheme.colors
    val type = RecompTheme.typography
    val spacing = RecompTheme.spacing
    Box(
        modifier =
            modifier
                .alpha(if (enabled) 1f else DISABLED_ALPHA)
                .recompPress(onClick = onClick, enabled = enabled)
                .border(spacing.border, colors.line, RecompTheme.shapes.sm)
                .padding(horizontal = spacing.md, vertical = spacing.sm),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text.uppercase(),
            style = type.label,
            color = colors.dim,
            textAlign = TextAlign.Center,
        )
    }
}
