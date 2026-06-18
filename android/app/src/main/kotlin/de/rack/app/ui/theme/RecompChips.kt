package de.rack.app.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val ChipPillShape = RoundedCornerShape(percent = 50)

// Kit `.chip` font size (11px), one step below the 12px mono label token.
private val ChipFontSize = 11.sp
private const val CHIP_BORDER_ALPHA = 0.4f

/**
 * A selectable filter chip from the kit (`.chip` / `.chip.active`). When [selected] it fills
 * volt with [RecompColors.bg] text; otherwise it is an outlined panel chip. Pass an [accent]
 * (e.g. a tag color from [tagColor]) to tint the unselected label + border for category chips
 * (kit `.chip.pull`). Mono, uppercased, with the shared press-scale.
 */
@Composable
fun RecompChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Color? = null,
) {
    val colors = RecompTheme.colors
    val spacing = RecompTheme.spacing
    val background = if (selected) colors.volt else colors.panel
    val border = if (selected) colors.volt else (accent?.copy(alpha = CHIP_BORDER_ALPHA) ?: colors.line)
    val content = if (selected) colors.bg else (accent ?: colors.dim)
    val weight = if (selected) FontWeight.SemiBold else FontWeight.Normal
    Text(
        text = label.uppercase(),
        style = RecompTheme.typography.label.copy(fontSize = ChipFontSize, fontWeight = weight),
        color = content,
        modifier =
            modifier
                .recompPress(onClick = onClick)
                .background(background, ChipPillShape)
                .border(spacing.border, border, ChipPillShape)
                .padding(horizontal = spacing.md, vertical = spacing.sm),
    )
}

/**
 * The horizontally scrollable chip rail (kit `.chipbar`) — lays its [content] chips in a row
 * with the kit's 8dp gap and lets them overflow off-screen. Place [RecompChip]s inside.
 */
@Composable
fun RecompChipRow(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    val spacing = RecompTheme.spacing
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        content = content,
    )
}
