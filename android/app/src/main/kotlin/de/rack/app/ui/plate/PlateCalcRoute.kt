package de.rack.app.ui.plate

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import de.rack.app.di.plateCalcViewModelFactory
import de.rack.app.ui.LocalAppContainer
import de.rack.app.ui.RackDestinations

/**
 * Registers the plate-calculator destination (Phase 13, docs/specs/spec-plate-calc-1rm.md).
 * The optional [RackDestinations.PLATE_CALC_WEIGHT_ARG] query argument pre-fills the target
 * weight from the logging surface's working weight. Extracted as a [NavGraphBuilder]
 * extension so the NavHost route table stays within the method-length guideline.
 */
fun NavGraphBuilder.plateCalcDestination(navController: NavHostController) {
    composable(
        route = RackDestinations.PLATE_CALC,
        arguments =
            listOf(
                navArgument(RackDestinations.PLATE_CALC_WEIGHT_ARG) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
    ) { entry ->
        val weight = entry.arguments?.getString(RackDestinations.PLATE_CALC_WEIGHT_ARG).orEmpty()
        PlateCalcRoute(initialWeight = weight, onBack = { navController.popBackStack() })
    }
}

/**
 * Hosts the plate-calculator screen: resolves the [PlateCalcViewModel] (seeded with the
 * route's [initialWeight]), observes its state, and forwards each edit and back event. All
 * persistence runs through the repository the ViewModel mediates; this route holds no logic.
 */
@Composable
fun PlateCalcRoute(
    initialWeight: String,
    onBack: () -> Unit,
) {
    val factory = plateCalcViewModelFactory(LocalAppContainer.current, initialWeight)
    val viewModel: PlateCalcViewModel = viewModel(factory = factory)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    PlateCalcScreen(
        state = uiState,
        actions =
            PlateCalcActions(
                onBack = onBack,
                onTargetChange = viewModel::onTargetChange,
                onBarWeightChange = viewModel::changeBarWeight,
                onPairCountChange = viewModel::changePairCount,
            ),
    )
}
