package de.rack.app.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import de.rack.app.di.AppContainer
import de.rack.app.di.appViewModelFactory
import de.rack.app.di.artifactViewerViewModelFactory
import de.rack.app.di.exerciseDetailViewModelFactory
import de.rack.app.ui.artifacts.ArtifactActions
import de.rack.app.ui.artifacts.ArtifactScreen
import de.rack.app.ui.artifacts.ArtifactViewModel
import de.rack.app.ui.artifacts.ArtifactViewerScreen
import de.rack.app.ui.artifacts.ArtifactViewerViewModel
import de.rack.app.ui.auth.AuthRoute
import de.rack.app.ui.auth.AuthViewModel
import de.rack.app.ui.auth.LoginScreen
import de.rack.app.ui.calendar.calendarDestination
import de.rack.app.ui.exercise.ExerciseDetailScreen
import de.rack.app.ui.exercise.ExerciseDetailViewModel
import de.rack.app.ui.exercise.ExerciseProgressRoute
import de.rack.app.ui.home.homeDestination
import de.rack.app.ui.keys.ApiKeyActions
import de.rack.app.ui.keys.ApiKeyScreen
import de.rack.app.ui.keys.ApiKeyState
import de.rack.app.ui.keys.ApiKeyViewModel
import de.rack.app.ui.plan.PlanScreen
import de.rack.app.ui.plan.PlanViewModel
import de.rack.app.ui.plan.planActions
import de.rack.app.ui.plate.plateCalcDestination
import de.rack.app.ui.session.SessionPlayerRoute
import de.rack.app.ui.settings.settingsDestination
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
    val backStackEntry by navController.currentBackStackEntryAsState()
    RackNavScaffold(
        currentRoute = backStackEntry?.destination?.route,
        onNavigate = { route -> navController.navigateTopLevel(route) },
        onSignOut = onSignOut,
    ) {
        SignedInNavGraph(navController = navController)
    }
}

/**
 * Navigates to a top-level dock destination with bottom-nav semantics: pop back to the start
 * destination saving its state, avoid stacking duplicate entries, and restore previously saved
 * state so switching tiles never grows the back stack.
 */
private fun NavHostController.navigateTopLevel(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun SignedInNavGraph(navController: NavHostController) {
    NavHost(navController = navController, startDestination = RackDestinations.PLAN) {
        composable(RackDestinations.PLAN) { PlanRoute(navController = navController) }
        homeDestination(navController)
        calendarDestination(navController)
        settingsDestination(navController)
        composable(RackDestinations.KEYS) {
            KeysRoute(onBack = { navController.popBackStack() })
        }
        composable(RackDestinations.ARTIFACTS) {
            ArtifactsRoute(
                onOpen = { id -> navController.navigate(RackDestinations.artifactViewerRoute(id)) },
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = RackDestinations.ARTIFACT_VIEWER,
            arguments = listOf(navArgument(RackDestinations.ARTIFACT_ID_ARG) { type = NavType.StringType }),
        ) { entry ->
            val artifactId = entry.arguments?.getString(RackDestinations.ARTIFACT_ID_ARG).orEmpty()
            ArtifactViewerRoute(artifactId = artifactId, onBack = { navController.popBackStack() })
        }
        composable(
            route = RackDestinations.EXERCISE_DETAIL,
            arguments = listOf(navArgument(RackDestinations.EXERCISE_ID_ARG) { type = NavType.StringType }),
        ) { entry ->
            val exerciseId = entry.arguments?.getString(RackDestinations.EXERCISE_ID_ARG).orEmpty()
            ExerciseDetailRoute(
                exerciseId = exerciseId,
                onBack = { navController.popBackStack() },
                onOpenProgress = { navController.navigate(RackDestinations.exerciseProgressRoute(exerciseId)) },
            )
        }
        composable(
            route = RackDestinations.EXERCISE_PROGRESS,
            arguments = listOf(navArgument(RackDestinations.EXERCISE_ID_ARG) { type = NavType.StringType }),
        ) { entry ->
            val exerciseId = entry.arguments?.getString(RackDestinations.EXERCISE_ID_ARG).orEmpty()
            ExerciseProgressRoute(
                container = LocalAppContainer.current,
                exerciseId = exerciseId,
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = RackDestinations.SESSION,
            arguments = listOf(navArgument(RackDestinations.SESSION_DAY_ID_ARG) { type = NavType.StringType }),
        ) { entry ->
            val dayId = entry.arguments?.getString(RackDestinations.SESSION_DAY_ID_ARG).orEmpty()
            SessionPlayerRoute(
                container = LocalAppContainer.current,
                dayId = dayId,
                onClose = { navController.popBackStack() },
            )
        }
        plateCalcDestination(navController)
    }
}

@Composable
private fun PlanRoute(navController: NavHostController) {
    val planViewModel: PlanViewModel = viewModel(factory = appViewModelFactory(LocalAppContainer.current))
    val planState by planViewModel.uiState.collectAsStateWithLifecycle()
    PlanScreen(
        state = planState,
        actions = planActions(navController, planViewModel),
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun ArtifactsRoute(
    onOpen: (String) -> Unit,
    onBack: () -> Unit,
) {
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
                onOpen = onOpen,
                onBack = onBack,
            ),
    )
}

@Composable
private fun ArtifactViewerRoute(
    artifactId: String,
    onBack: () -> Unit,
) {
    val factory = artifactViewerViewModelFactory(LocalAppContainer.current, artifactId, ::decodePng)
    val viewModel: ArtifactViewerViewModel = viewModel(key = artifactId, factory = factory)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ArtifactViewerScreen(state = uiState, onRetry = viewModel::load, onBack = onBack)
}

@Composable
private fun ExerciseDetailRoute(
    exerciseId: String,
    onBack: () -> Unit,
    onOpenProgress: () -> Unit,
) {
    val factory = exerciseDetailViewModelFactory(LocalAppContainer.current, exerciseId, ::decodePng)
    val viewModel: ExerciseDetailViewModel = viewModel(key = exerciseId, factory = factory)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ExerciseDetailScreen(
        state = uiState,
        onRetry = viewModel::load,
        onBack = onBack,
        onOpenProgress = onOpenProgress,
    )
}

/** Decode image bytes into a Compose [ImageBitmap]; null when the bytes are not a valid bitmap. */
private fun decodePng(bytes: ByteArray): ImageBitmap? =
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()

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
