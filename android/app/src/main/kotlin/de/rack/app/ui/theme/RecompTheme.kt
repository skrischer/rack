package de.rack.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Bespoke Recomp dark theme. Encodes docs/design-tokens.md as custom token objects
 * (colors, type, shapes, spacing) exposed via CompositionLocals and read through the
 * [RecompTheme] accessor. Not Material 3 dynamic color — there is no wallpaper/dynamic
 * palette and no light variant yet; the single accent is volt and on-volt content is bg.
 *
 * A minimal Material 3 [darkColorScheme] is still installed so stray Material
 * components (and the status-bar scrim) stay dark and on-brand, but app UI should read
 * the [RecompTheme] tokens directly rather than the Material color roles.
 */
object RecompTheme {
    val colors: RecompColors
        @Composable
        @ReadOnlyComposable
        get() = LocalRecompColors.current

    val typography: RecompTypography
        @Composable
        @ReadOnlyComposable
        get() = LocalRecompTypography.current

    val shapes: RecompShapes
        @Composable
        @ReadOnlyComposable
        get() = LocalRecompShapes.current

    val spacing: RecompSpacing
        @Composable
        @ReadOnlyComposable
        get() = LocalRecompSpacing.current
}

val LocalRecompColors = staticCompositionLocalOf { recompDarkColors }
val LocalRecompTypography = staticCompositionLocalOf { recompTypography }
val LocalRecompShapes = staticCompositionLocalOf { recompShapes }
val LocalRecompSpacing = staticCompositionLocalOf { recompSpacing }

@Composable
fun RecompTheme(content: @Composable () -> Unit) {
    val colors = recompDarkColors
    CompositionLocalProvider(
        LocalRecompColors provides colors,
        LocalRecompTypography provides recompTypography,
        LocalRecompShapes provides recompShapes,
        LocalRecompSpacing provides recompSpacing,
    ) {
        MaterialTheme(
            colorScheme =
                darkColorScheme(
                    primary = colors.volt,
                    onPrimary = colors.bg,
                    background = colors.bg,
                    onBackground = colors.txt,
                    surface = colors.panel,
                    onSurface = colors.txt,
                    surfaceVariant = colors.panelElevated,
                    outline = colors.line,
                    error = colors.legs,
                ),
            content = content,
        )
    }
}
