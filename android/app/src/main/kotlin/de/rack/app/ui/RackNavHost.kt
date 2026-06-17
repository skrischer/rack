package de.rack.app.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import de.rack.app.di.AppContainer

/**
 * Single-Activity Compose Navigation host. The [container] is exposed through
 * [LocalAppContainer] so destinations resolve their repositories. Destinations
 * are placeholders that the auth (#21), plan-view (#22), and logging (#23) issues
 * replace with real screens.
 */
@Composable
fun RackNavHost(
    container: AppContainer,
    navController: NavHostController = rememberNavController(),
) {
    CompositionLocalProvider(LocalAppContainer provides container) {
        NavHost(navController = navController, startDestination = RackDestinations.LOGIN) {
            composable(RackDestinations.LOGIN) { Text(text = "Login") }
            composable(RackDestinations.PLAN) { Text(text = "Plan") }
        }
    }
}
