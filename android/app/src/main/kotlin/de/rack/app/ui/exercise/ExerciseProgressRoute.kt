package de.rack.app.ui.exercise

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.rack.app.di.AppContainer
import de.rack.app.di.exerciseProgressViewModelFactory

/**
 * Hosts the per-exercise progress screen (Phase 11): resolves the route-scoped
 * [ExerciseProgressViewModel] for [exerciseId] from the [container] and observes its
 * state. Kept out of the NavHost (like [de.rack.app.ui.home.HomeRoute] and the session
 * player route) so the navigation graph stays a thin route table.
 */
@Composable
fun ExerciseProgressRoute(
    container: AppContainer,
    exerciseId: String,
    onBack: () -> Unit,
) {
    val factory = exerciseProgressViewModelFactory(container, exerciseId)
    val viewModel: ExerciseProgressViewModel = viewModel(key = exerciseId, factory = factory)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ExerciseProgressScreen(state = uiState, onRetry = viewModel::load, onBack = onBack)
}
