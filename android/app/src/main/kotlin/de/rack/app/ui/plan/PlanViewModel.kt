package de.rack.app.ui.plan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.rack.app.data.TrainingRepository
import de.rack.app.domain.Plan
import de.rack.app.domain.PlanDay
import de.rack.app.domain.PlanExercise
import de.rack.app.ui.theme.SupersetKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * One renderable entry in a day's exercise list: a group of consecutive
 * exercises that either stand alone ([SupersetKind.NONE]) or share a
 * `superset_label` ([SupersetKind.SUPERSET] / [SupersetKind.CIRCUIT]). Derived
 * client-side from the position-ordered exercises (it is not stored).
 */
data class ExerciseGroup(
    val kind: SupersetKind,
    val exercises: List<PlanExercise>,
)

/** A plan day with its exercises already grouped into superset/circuit runs. */
data class DayContent(
    val day: PlanDay,
    val groups: List<ExerciseGroup>,
)

/** The loaded plan view: the user's plans, the selected plan, and its days. */
data class PlanContent(
    val plans: List<Plan>,
    val selectedPlanId: String,
    val days: List<DayContent>,
)

/** Plan-view UI state observed by the screen: loading, content, or error. */
sealed interface PlanUiState {
    data object Loading : PlanUiState

    data class Content(val content: PlanContent) : PlanUiState

    data class Error(val message: String) : PlanUiState

    /** No plans exist for the signed-in user (RLS-empty or not yet authored). */
    data object Empty : PlanUiState
}

/**
 * Reads the signed-in user's plans, days, and exercises through the
 * [TrainingRepository] (all under RLS — no `user_id` is ever sent) and exposes
 * them as [StateFlow]. Selecting a plan reloads its days, each carrying its
 * exercises grouped client-side into superset/circuit runs. No Supabase access
 * happens in Composables; the screen observes [uiState] and emits events only.
 */
class PlanViewModel(
    private val repository: TrainingRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow<PlanUiState>(PlanUiState.Loading)
    val uiState: StateFlow<PlanUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    /** (Re)load the plan list and the first plan's days. */
    fun load() {
        _uiState.value = PlanUiState.Loading
        viewModelScope.launch {
            runCatching { repository.getPlans() }
                .onSuccess { plans -> onPlansLoaded(plans) }
                .onFailure { error -> _uiState.value = PlanUiState.Error(messageFor(error)) }
        }
    }

    /** Switch to [planId] and load its days, keeping the current plan list. */
    fun selectPlan(planId: String) {
        val current = (_uiState.value as? PlanUiState.Content)?.content ?: return
        if (current.selectedPlanId == planId) return
        loadDaysFor(current.plans, planId)
    }

    private suspend fun onPlansLoaded(plans: List<Plan>) {
        val first = plans.firstOrNull()
        if (first == null) {
            _uiState.value = PlanUiState.Empty
            return
        }
        setDays(plans, first.id)
    }

    private fun loadDaysFor(
        plans: List<Plan>,
        planId: String,
    ) {
        viewModelScope.launch {
            runCatching { setDays(plans, planId) }
                .onFailure { error -> _uiState.value = PlanUiState.Error(messageFor(error)) }
        }
    }

    private suspend fun setDays(
        plans: List<Plan>,
        planId: String,
    ) {
        val days = repository.getPlanDays(planId).map { day -> toDayContent(day) }
        _uiState.value = PlanUiState.Content(PlanContent(plans, planId, days))
    }

    private suspend fun toDayContent(day: PlanDay): DayContent {
        val exercises = repository.getPlanExercises(day.id)
        return DayContent(day = day, groups = groupExercises(exercises))
    }

    private fun messageFor(error: Throwable): String = error.message?.takeIf { it.isNotBlank() } ?: GENERIC_ERROR

    private companion object {
        const val GENERIC_ERROR = "Could not load your plans. Check your connection and try again."
    }
}
