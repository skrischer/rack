package de.rack.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import de.rack.app.di.AppContainer
import de.rack.app.di.appViewModelFactory
import de.rack.app.ui.auth.AuthRoute
import de.rack.app.ui.auth.AuthViewModel
import de.rack.app.ui.auth.LoginScreen
import de.rack.app.ui.plan.PlanScreen
import de.rack.app.ui.plan.PlanViewModel
import de.rack.app.ui.theme.RecompTheme

/**
 * Single-Activity Compose root. The [container] is exposed through
 * [LocalAppContainer] so screens resolve their repositories. Top-level routing
 * is driven by the persisted auth session: signed out shows the login screen,
 * signed in shows the navigation graph. The plan view is the signed-in home;
 * logging is added in #23.
 */
@Composable
fun RackNavHost(
    container: AppContainer,
    navController: NavHostController = rememberNavController(),
) {
    CompositionLocalProvider(LocalAppContainer provides container) {
        val authViewModel: AuthViewModel = viewModel(factory = appViewModelFactory(container))
        val route by authViewModel.route.collectAsStateWithLifecycle()
        when (route) {
            AuthRoute.Loading -> LoadingScreen()
            AuthRoute.SignedOut -> {
                val loginState by authViewModel.loginState.collectAsStateWithLifecycle()
                LoginScreen(
                    state = loginState,
                    onSignIn = authViewModel::signIn,
                    onDismissError = authViewModel::dismissError,
                )
            }
            AuthRoute.SignedIn -> SignedInNavHost(navController, authViewModel::signOut)
        }
    }
}

@Composable
private fun SignedInNavHost(
    navController: NavHostController,
    onSignOut: () -> Unit,
) {
    NavHost(navController = navController, startDestination = RackDestinations.PLAN) {
        composable(RackDestinations.PLAN) {
            val planViewModel: PlanViewModel = viewModel(factory = appViewModelFactory(LocalAppContainer.current))
            val planState by planViewModel.uiState.collectAsStateWithLifecycle()
            PlanScreen(
                state = planState,
                onSelectPlan = planViewModel::selectPlan,
                onRetry = planViewModel::load,
                onSignOut = onSignOut,
            )
        }
    }
}

@Composable
private fun LoadingScreen() {
    Box(
        modifier = Modifier.fillMaxSize().background(RecompTheme.colors.bg),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(color = RecompTheme.colors.volt)
    }
}
