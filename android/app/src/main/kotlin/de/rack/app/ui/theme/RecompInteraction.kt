package de.rack.app.ui.theme

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.scale

/**
 * The Recomp press feedback (docs/design-tokens.md "Motion"): an indication-less click
 * that scales the element to [RecompMotion.PRESS_SCALE] while pressed and eases back on
 * release. Shared by every tappable Recomp component (buttons, chips, steppers, set
 * checks, nav tiles) so press feel is uniform — no Material ripple, just the scale.
 *
 * Presentational only: it wires the supplied [onClick]; the component owns no other logic.
 */
fun Modifier.recompPress(
    onClick: () -> Unit,
    enabled: Boolean = true,
): Modifier =
    composed {
        val interaction = remember { MutableInteractionSource() }
        val pressed by interaction.collectIsPressedAsState()
        val scale by animateFloatAsState(
            targetValue = if (pressed) RecompMotion.PRESS_SCALE else 1f,
            label = "recompPress",
        )
        this
            .scale(scale)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            )
    }

/**
 * An indication-less tap with no scale — for elements the kit does not animate on press
 * (list/menu rows, segmented-toggle options, nav tiles). Keeps the dark surfaces free of
 * the Material ripple while still being clickable.
 */
fun Modifier.recompClick(
    onClick: () -> Unit,
    enabled: Boolean = true,
): Modifier =
    composed {
        val interaction = remember { MutableInteractionSource() }
        this.clickable(
            interactionSource = interaction,
            indication = null,
            enabled = enabled,
            onClick = onClick,
        )
    }
