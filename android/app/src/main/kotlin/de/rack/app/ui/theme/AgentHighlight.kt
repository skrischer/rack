package de.rack.app.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape

/**
 * The transient volt/lime tint that marks a row whose latest change came from the
 * agent (`source='agent'`). Whether a row is highlighted and for how long is owned
 * by the [de.rack.app.domain.HighlightTracker] in the ViewModel layer (the 3 s
 * fade lives there as a named constant); this modifier only renders the supplied
 * boolean, animating the volt accent in and back out as it flips. Composables hold
 * no timing logic — they read [highlighted] from `StateFlow`.
 */
@Composable
fun Modifier.agentHighlight(
    highlighted: Boolean,
    shape: Shape = RectangleShape,
): Modifier =
    composed {
        val target =
            if (highlighted) RecompTheme.colors.volt.copy(alpha = HIGHLIGHT_TINT_ALPHA) else Color.Transparent
        val tint by animateColorAsState(
            targetValue = target,
            animationSpec = tween(RecompMotion.ENTER_DURATION_MS),
            label = "agentHighlight",
        )
        background(color = tint, shape = shape)
    }

/** Volt tint strength on a highlighted row — present enough to catch the eye, not solid. */
private const val HIGHLIGHT_TINT_ALPHA = 0.22f
