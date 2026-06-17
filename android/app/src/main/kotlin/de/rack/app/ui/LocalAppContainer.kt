package de.rack.app.ui

import androidx.compose.runtime.staticCompositionLocalOf
import de.rack.app.di.AppContainer

/**
 * Exposes the process-wide [AppContainer] to the composition so screens added in
 * later issues (#21/#22/#23) obtain their repositories without per-screen wiring.
 * Provided once at the navigation root.
 */
val LocalAppContainer = staticCompositionLocalOf<AppContainer> { error("AppContainer not provided") }
