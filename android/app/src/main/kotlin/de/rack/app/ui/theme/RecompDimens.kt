package de.rack.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Radius tokens from docs/design-tokens.md: sm 8 (inputs, history, reset), md 9
 * (day chip, primary button), lg 10 (tabs), xl 16 (day/note cards).
 */
@Immutable
data class RecompShapes(
    val sm: RoundedCornerShape = RoundedCornerShape(8.dp),
    val md: RoundedCornerShape = RoundedCornerShape(9.dp),
    val lg: RoundedCornerShape = RoundedCornerShape(10.dp),
    val xl: RoundedCornerShape = RoundedCornerShape(16.dp),
)

val recompShapes: RecompShapes = RecompShapes()

/**
 * Spacing tokens: the loose 2px-grid scale from docs/design-tokens.md, plus the
 * named layout values (screen gutter, content max width, 1px borders).
 */
@Immutable
data class RecompSpacing(
    val xxs: Dp = 4.dp,
    val xs: Dp = 6.dp,
    val sm: Dp = 8.dp,
    val md: Dp = 12.dp,
    val lg: Dp = 16.dp,
    val xl: Dp = 18.dp,
    val xxl: Dp = 22.dp,
    val xxxl: Dp = 26.dp,
    val huge: Dp = 38.dp,
    /** Horizontal card inset (20) per the tokens (head/row/foot all 20 wide). */
    val cardInsetH: Dp = 20.dp,
    /** Card row vertical inset (15) per the tokens. */
    val rowInsetV: Dp = 15.dp,
    /** Day-number chip side (38) per the tokens. */
    val dayChip: Dp = 38.dp,
    /** Screen gutter (18) per the tokens. */
    val gutter: Dp = 18.dp,
    /** Content max width (780) — phone-first single column. */
    val contentMaxWidth: Dp = 780.dp,
    /** Borders and dividers are always 1px. */
    val border: Dp = 1.dp,
)

val recompSpacing: RecompSpacing = RecompSpacing()
