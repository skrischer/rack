package de.rack.app.ui.exercise

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.rack.app.data.DashboardRepository
import de.rack.app.domain.ExerciseProgressPoint
import de.rack.app.domain.LoggedSet
import de.rack.app.domain.exerciseProgress
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Per-exercise progress UI state (spec-dashboards.md, Phase 11). The screen plots
 * top-set weight and per-session volume over the logged dates; with zero or one
 * logged date a line chart has nothing to connect, so that case is its own state
 * ([InsufficientData]) and the screen shows a Recomp empty pane instead of handing
 * Vico an unguarded series.
 */
sealed interface ExerciseProgressUiState {
    data object Loading : ExerciseProgressUiState

    /** Two or more logged dates: render the chart plus the table fallback. */
    data class Content(
        val exerciseName: String,
        val points: List<ExerciseProgressPoint>,
    ) : ExerciseProgressUiState

    /** Zero or one logged date: too few points to plot a trend. */
    data class InsufficientData(
        val exerciseName: String,
        val points: List<ExerciseProgressPoint>,
    ) : ExerciseProgressUiState

    data class Error(val message: String) : ExerciseProgressUiState
}

/**
 * Resolves one catalog exercise's logged history into progress points for the
 * progress screen. It reads the signed-in user's logged sets through the
 * [DashboardRepository] (anon key + user JWT only; RLS scopes the rows) and runs
 * the pure [exerciseProgress] aggregation — no Supabase access and no charting
 * happen here, only state. With fewer than [MIN_CHART_POINTS] logged dates the
 * state is [ExerciseProgressUiState.InsufficientData] so the screen never feeds an
 * empty/single-point series to the chart. See docs/specs/spec-dashboards.md.
 */
class ExerciseProgressViewModel(
    private val repository: DashboardRepository,
    private val exerciseId: String,
) : ViewModel() {
    private val _uiState = MutableStateFlow<ExerciseProgressUiState>(ExerciseProgressUiState.Loading)
    val uiState: StateFlow<ExerciseProgressUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    /** (Re)load and re-aggregate this exercise's progress; retried from the error pane. */
    fun load() {
        _uiState.value = ExerciseProgressUiState.Loading
        viewModelScope.launch {
            runCatching { repository.getLoggedSets() }
                .onSuccess { sets -> _uiState.value = resolve(sets) }
                .onFailure { error -> _uiState.value = errorState(error.message) }
        }
    }

    private fun resolve(sets: List<LoggedSet>): ExerciseProgressUiState {
        val points = exerciseProgress(sets, exerciseId)
        val name = sets.firstOrNull { it.exerciseId == exerciseId }?.exerciseName.orEmpty()
        return if (points.size >= MIN_CHART_POINTS) {
            ExerciseProgressUiState.Content(exerciseName = name, points = points)
        } else {
            ExerciseProgressUiState.InsufficientData(exerciseName = name, points = points)
        }
    }

    private fun errorState(message: String?): ExerciseProgressUiState.Error =
        ExerciseProgressUiState.Error(message?.takeIf { it.isNotBlank() } ?: GENERIC_ERROR)

    private companion object {
        /** A line needs at least two points to draw a trend. */
        const val MIN_CHART_POINTS = 2
        const val GENERIC_ERROR = "Could not load progress. Check your connection and try again."
    }
}
