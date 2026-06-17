package de.rack.app.ui.logging

import androidx.compose.runtime.Immutable

/**
 * The logging events the plan screen forwards to the [LoggingViewModel], bundled
 * so the deeply nested exercise rows take one parameter instead of many lambdas.
 * Each callback is keyed by `plan_exercise_id` at the call site.
 */
@Immutable
data class LoggingHandlers(
    val prepare: (planExerciseId: String, setCount: Int) -> Unit,
    val onWeightChange: (planExerciseId: String, value: String) -> Unit,
    val onRepChange: (planExerciseId: String, index: Int, value: String) -> Unit,
    val onToggleHistory: (planExerciseId: String) -> Unit,
    val onLog: (planExerciseId: String) -> Unit,
)

/**
 * The logging state plus its handlers, bundled so the plan screen and its day
 * cards forward one parameter down to the exercise rows.
 */
@Immutable
data class LoggingSection(
    val state: LoggingUiState,
    val handlers: LoggingHandlers,
)
