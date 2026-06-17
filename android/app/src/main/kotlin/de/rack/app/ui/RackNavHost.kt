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
import de.rack.app.ui.artifacts.ArtifactActions
import de.rack.app.ui.artifacts.ArtifactScreen
import de.rack.app.ui.artifacts.ArtifactViewModel
import de.rack.app.ui.auth.AuthRoute
import de.rack.app.ui.auth.AuthViewModel
import de.rack.app.ui.auth.LoginScreen
import de.rack.app.ui.keys.ApiKeyActions
import de.rack.app.ui.keys.ApiKeyScreen
import de.rack.app.ui.keys.ApiKeyState
import de.rack.app.ui.keys.ApiKeyViewModel
import de.rack.app.ui.logging.LoggingHandlers
import de.rack.app.ui.logging.LoggingSection
import de.rack.app.ui.logging.LoggingViewModel
import de.rack.app.ui.plan.PlanActions
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
            val factory = appViewModelFactory(LocalAppContainer.current)
            val planViewModel: PlanViewModel = viewModel(factory = factory)
            val loggingViewModel: LoggingViewModel = viewModel(factory = factory)
            val planState by planViewModel.uiState.collectAsStateWithLifecycle()
            val loggingState by loggingViewModel.uiState.collectAsStateWithLifecycle()
            PlanScreen(
                state = planState,
                logging =
                    LoggingSection(
                        state = loggingState,
                        handlers =
                            LoggingHandlers(
                                prepare = loggingViewModel::prepare,
                                onWeightChange = loggingViewModel::onWeightChange,
                                onRepChange = loggingViewModel::onRepChange,
                                onToggleHistory = loggingViewModel::toggleHistory,
                                onLog = loggingViewModel::log,
                            ),
                    ),
                actions =
                    PlanActions(
                        onSelectPlan = planViewModel::selectPlan,
                        onRetry = planViewModel::load,
                        onSignOut = onSignOut,
                        onOpenKeys = { navController.navigate(RackDestinations.KEYS) },
                        onOpenArtifacts = { navController.navigate(RackDestinations.ARTIFACTS) },
                    ),
            )
        }
        composable(RackDestinations.KEYS) {
            KeysRoute(onBack = { navController.popBackStack() })
        }
        composable(RackDestinations.ARTIFACTS) {
            ArtifactsRoute(onBack = { navController.popBackStack() })
        }
    }
}

@Composable
private fun ArtifactsRoute(onBack: () -> Unit) {
    val viewModel: ArtifactViewModel = viewModel(factory = appViewModelFactory(LocalAppContainer.current))
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    ArtifactScreen(
        state = uiState,
        isRefreshing = isRefreshing,
        actions =
            ArtifactActions(
                onRefresh = viewModel::refresh,
                onRetry = viewModel::load,
                onBack = onBack,
            ),
    )
}

@Composable
private fun KeysRoute(onBack: () -> Unit) {
    val viewModel: ApiKeyViewModel = viewModel(factory = appViewModelFactory(LocalAppContainer.current))
    val listState by viewModel.listState.collectAsStateWithLifecycle()
    val revealedKey by viewModel.revealedKey.collectAsStateWithLifecycle()
    val isCreating by viewModel.isCreating.collectAsStateWithLifecycle()
    ApiKeyScreen(
        state =
            ApiKeyState(
                list = listState,
                isCreating = isCreating,
                revealedKey = revealedKey,
                endpointUrl = viewModel.mcpEndpointUrl,
            ),
        actions =
            ApiKeyActions(
                onCreate = viewModel::create,
                onRevoke = viewModel::revoke,
                onRetry = viewModel::load,
                onDismissReveal = viewModel::clearRevealedKey,
                onBack = {
                    viewModel.clearRevealedKey()
                    onBack()
                },
            ),
    )
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
