package de.rack.app.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
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
import de.rack.app.ui.exercise.ExerciseDetailScreen
import de.rack.app.ui.exercise.ExerciseDetailViewModel
import de.rack.app.ui.keys.ApiKeyActions
import de.rack.app.ui.keys.ApiKeyScreen
import de.rack.app.ui.keys.ApiKeyState
import de.rack.app.ui.keys.ApiKeyViewModel
import de.rack.app.ui.logging.LoggingHandlers
import de.rack.app.ui.logging.LoggingSection
import de.rack.app.ui.logging.LoggingViewModel
import de.rack.app.ui.plan.PlanActions
import de.rack.app.ui.plan.PlanScreen
import de.rack.app.ui.plan.PlanUiState
import de.rack.app.ui.plan.PlanViewModel
import de.rack.app.ui.plan.findLoggedExerciseContext
import de.rack.app.ui.session.SessionPlayerRoute
import de.rack.app.ui.theme.RecompTheme
import de.rack.app.ui.timer.RestCompletionVibration
import de.rack.app.ui.timer.TimerBar
import de.rack.app.ui.timer.TimerBarActions
import de.rack.app.ui.timer.TimerViewModel
import de.rack.app.ui.timer.rememberNotificationPermission

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
            PlanRoute(navController = navController, onSignOut = onSignOut)
        }
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
            ExerciseDetailRoute(exerciseId = exerciseId, onBack = { navController.popBackStack() })
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
    }
}

@Composable
private fun PlanRoute(
    navController: NavHostController,
    onSignOut: () -> Unit,
) {
    val factory = appViewModelFactory(LocalAppContainer.current)
    val planViewModel: PlanViewModel = viewModel(factory = factory)
    val loggingViewModel: LoggingViewModel = viewModel(factory = factory)
    val timerViewModel: TimerViewModel = viewModel(factory = factory)
    val planState by planViewModel.uiState.collectAsStateWithLifecycle()
    val loggingState by loggingViewModel.uiState.collectAsStateWithLifecycle()
    val timerState by timerViewModel.uiState.collectAsStateWithLifecycle()
    val permission = rememberNotificationPermission()
    RestCompletionVibration(restFinished = timerViewModel.restFinished)
    Column(modifier = Modifier.fillMaxSize()) {
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
                            onLog = { id ->
                                loggingViewModel.log(id)
                                permission.request()
                                startRestFor(timerViewModel, planState, id)
                            },
                        ),
                ),
            actions =
                PlanActions(
                    onSelectPlan = planViewModel::selectPlan,
                    onRetry = planViewModel::load,
                    onSignOut = onSignOut,
                    onOpenKeys = { navController.navigate(RackDestinations.KEYS) },
                    onOpenArtifacts = { navController.navigate(RackDestinations.ARTIFACTS) },
                    onOpenExercise = { id -> navController.navigate(RackDestinations.exerciseDetailRoute(id)) },
                    onStartSession = { dayId -> navController.navigate(RackDestinations.sessionRoute(dayId)) },
                ),
            modifier = Modifier.weight(1f),
        )
        TimerBar(
            state = timerState,
            notificationsDenied = !permission.isGranted,
            actions =
                TimerBarActions(
                    onAdd = timerViewModel::addRest,
                    onSubtract = timerViewModel::subtractRest,
                    onSkip = timerViewModel::skipRest,
                    onRestart = timerViewModel::restartRest,
                    onEndSession = timerViewModel::stopSession,
                ),
        )
    }
}

/**
 * Resolve the logged exercise's group context from the on-screen plan and auto-start
 * its rest + rotation cue (docs/specs/spec-timers.md). A pure lookup feeds the
 * resolution that lives in the [TimerViewModel]; nothing here is business logic.
 */
private fun startRestFor(
    timerViewModel: TimerViewModel,
    planState: PlanUiState,
    planExerciseId: String,
) {
    val content = (planState as? PlanUiState.Content)?.content ?: return
    val context = findLoggedExerciseContext(content.days, planExerciseId) ?: return
    val category = context.group.getOrNull(context.index)?.category
    timerViewModel.onSetLogged(category = category, context = context)
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
) {
    val factory = exerciseDetailViewModelFactory(LocalAppContainer.current, exerciseId, ::decodePng)
    val viewModel: ExerciseDetailViewModel = viewModel(key = exerciseId, factory = factory)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ExerciseDetailScreen(state = uiState, onRetry = viewModel::load, onBack = onBack)
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
