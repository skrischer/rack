package de.rack.app.ui.plan

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.rack.app.data.RealtimeRepository
import de.rack.app.data.TrainingRepository
import de.rack.app.domain.HighlightTracker
import de.rack.app.domain.Plan
import de.rack.app.domain.PlanDay
import de.rack.app.domain.PlanExercise
import de.rack.app.domain.RealtimeChange
import de.rack.app.domain.SOURCE_AGENT
import de.rack.app.domain.SyncedTable
import de.rack.app.ui.theme.SupersetKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
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

/**
 * The loaded plan view: the user's plans, the selected plan, and its days, plus the
 * ids of rows ([Plan.id], [PlanDay.id], [PlanExercise.id]) that an agent just
 * touched and are transiently highlighted. The set is driven by the
 * [HighlightTracker] and clears itself after the fade; Composables only read it.
 */
data class PlanContent(
    val plans: List<Plan>,
    val selectedPlanId: String,
    val days: List<DayContent>,
    val highlightedIds: Set<String> = emptySet(),
)

/**
 * The plan-screen actions, bundled so the screen takes one parameter for plan
 * selection, retry, and sign-out instead of three separate lambdas.
 */
@Immutable
data class PlanActions(
    val onSelectPlan: (String) -> Unit,
    val onRetry: () -> Unit,
    val onSignOut: () -> Unit,
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
 *
 * It also consumes the live [RealtimeRepository.changes] stream for the
 * `plans`, `plan_days`, and `plan_exercises` tables: an incoming change re-reads
 * the current plan slice (last-write-wins re-read, keeping the join semantics) and,
 * when the payload carries `source='agent'`, flags the touched row for the
 * transient highlight via the [HighlightTracker] (3 s fade owned in that layer).
 */
class PlanViewModel(
    private val repository: TrainingRepository,
    realtime: RealtimeRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow<PlanUiState>(PlanUiState.Loading)
    val uiState: StateFlow<PlanUiState> = _uiState.asStateFlow()

    private val highlights = HighlightTracker(viewModelScope)

    init {
        load()
        viewModelScope.launch {
            realtime.changes().filter { it.table in PLAN_TABLES }.collect(::reconcileRealtime)
        }
        viewModelScope.launch {
            highlights.highlighted.collect(::projectHighlights)
        }
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
        highlightedIds: Set<String> = emptySet(),
    ) {
        val days = repository.getPlanDays(planId).map { day -> toDayContent(day) }
        _uiState.value = PlanUiState.Content(PlanContent(plans, planId, days, highlightedIds))
    }

    private suspend fun toDayContent(day: PlanDay): DayContent {
        val exercises = repository.getPlanExercises(day.id)
        return DayContent(day = day, groups = groupExercises(exercises))
    }

    /**
     * Re-read the current plan slice (last-write-wins) so the live [change] is
     * reflected, then flag an agent-touched row for the transient highlight; the
     * app's own `source='app'` edit re-reads but is never highlighted. Runs only
     * while a plan is on screen — there is nothing to reconcile otherwise. A change
     * to `plans` also re-reads the plan list so a renamed/added plan chip updates.
     */
    private fun reconcileRealtime(change: RealtimeChange) {
        val current = (_uiState.value as? PlanUiState.Content)?.content ?: return
        change.rowId?.takeIf { change.source == SOURCE_AGENT }?.let(highlights::flag)
        viewModelScope.launch {
            runCatching {
                val plans = if (change.table == SyncedTable.PLANS) repository.getPlans() else current.plans
                setDays(plans, current.selectedPlanId, highlights.highlighted.value)
            }.onFailure { /* keep the last good content; the next change re-reads */ }
        }
    }

    /** Re-project the currently highlighted ids onto the loaded content. */
    private fun projectHighlights(ids: Set<String>) {
        val content = (_uiState.value as? PlanUiState.Content)?.content ?: return
        _uiState.value = PlanUiState.Content(content.copy(highlightedIds = ids))
    }

    private fun messageFor(error: Throwable): String = error.message?.takeIf { it.isNotBlank() } ?: GENERIC_ERROR

    private companion object {
        const val GENERIC_ERROR = "Could not load your plans. Check your connection and try again."
        val PLAN_TABLES = setOf(SyncedTable.PLANS, SyncedTable.PLAN_DAYS, SyncedTable.PLAN_EXERCISES)
    }
}
