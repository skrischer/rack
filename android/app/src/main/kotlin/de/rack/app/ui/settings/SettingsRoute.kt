package de.rack.app.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import de.rack.app.di.AppContainer
import de.rack.app.di.appViewModelFactory
import de.rack.app.ui.LocalAppContainer
import de.rack.app.ui.RackDestinations

/**
 * Registers the Settings destination (Phase 12, docs/specs/spec-settings.md).
 * Extracted as a [NavGraphBuilder] extension so the NavHost route table stays
 * within the method-length guideline.
 */
fun NavGraphBuilder.settingsDestination(navController: NavHostController) {
    composable(RackDestinations.SETTINGS) {
        SettingsRoute(
            container = LocalAppContainer.current,
            onBack = { navController.popBackStack() },
        )
    }
}

/**
 * Hosts the Settings screen: resolves the [SettingsViewModel] from the [container],
 * observes its [SettingsViewModel.uiState], and forwards each edit intent and the
 * retry to the ViewModel. All persistence runs through the repository the ViewModel
 * mediates; this route holds no business logic.
 */
@Composable
fun SettingsRoute(
    container: AppContainer,
    onBack: () -> Unit,
) {
    val viewModel: SettingsViewModel = viewModel(factory = appViewModelFactory(container))
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    SettingsScreen(
        state = uiState,
        actions =
            SettingsActions(
                onRetry = viewModel::load,
                onBack = onBack,
                onSelectUnit = viewModel::setWeightUnit,
                onSelectTheme = viewModel::setTheme,
                onSetRestSeconds = viewModel::setRestSeconds,
                onDisplayNameChange = viewModel::setDisplayName,
            ),
    )
}
