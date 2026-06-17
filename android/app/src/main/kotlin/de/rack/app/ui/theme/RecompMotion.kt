package de.rack.app.ui.theme

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing

/**
 * Motion tokens from docs/design-tokens.md. Short, eased, no bounce: the standard
 * transition is 150ms ease, press feedback scales to 0.97, content enter is 250ms
 * with a 6dp translateY rise. Durations are in milliseconds.
 */
object RecompMotion {
    val easing: Easing = FastOutSlowInEasing
    const val STANDARD_DURATION_MS: Int = 150
    const val ENTER_DURATION_MS: Int = 250
    const val PRESS_SCALE: Float = 0.97f
    const val ENTER_OFFSET_DP: Int = 6
}
