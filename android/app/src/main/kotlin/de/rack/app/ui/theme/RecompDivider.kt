package de.rack.app.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * The pervasive 1px `--line` separator from the Recomp kit (docs/design-tokens.md:
 * "Borders are always 1px solid line"). Used between list/card rows, under sheet tiles,
 * and anywhere the designs draw a hairline. Full width by default; pass a [modifier] to
 * constrain it.
 */
@Composable
fun RecompDivider(modifier: Modifier = Modifier) {
    val colors = RecompTheme.colors
    val spacing = RecompTheme.spacing
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(spacing.border)
                .background(colors.line),
    )
}
