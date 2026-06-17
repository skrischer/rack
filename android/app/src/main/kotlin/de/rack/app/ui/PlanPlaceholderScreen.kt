package de.rack.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import de.rack.app.ui.theme.RecompTheme
import de.rack.app.ui.theme.RecompThemeShowcase

/**
 * Temporary signed-in home: the Recomp theme showcase plus a visible sign-out
 * action, so the auth round-trip (sign in -> signed-in area -> sign out) is fully
 * exercisable until the real plan view replaces it in #22.
 */
@Composable
fun PlanPlaceholderScreen(
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = RecompTheme.colors
    Column(modifier = modifier.fillMaxSize().background(colors.bg)) {
        Box(modifier = Modifier.weight(1f)) {
            RecompThemeShowcase()
        }
        SignOutBar(onSignOut = onSignOut)
    }
}

@Composable
private fun SignOutBar(onSignOut: () -> Unit) {
    val colors = RecompTheme.colors
    val type = RecompTheme.typography
    val spacing = RecompTheme.spacing
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(colors.bg)
                .padding(horizontal = spacing.gutter, vertical = spacing.md),
        horizontalArrangement = Arrangement.End,
    ) {
        Text(
            text = "SIGN OUT",
            style = type.label,
            color = colors.dim,
            modifier =
                Modifier
                    .border(spacing.border, colors.line, RecompTheme.shapes.sm)
                    .clickable(onClick = onSignOut)
                    .padding(horizontal = spacing.lg, vertical = spacing.sm)
                    .align(Alignment.CenterVertically),
        )
    }
}
