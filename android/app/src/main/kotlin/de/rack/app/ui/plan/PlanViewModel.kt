package de.rack.app.ui.plan

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.rack.app.data.AppLifecycleObserver
import de.rack.app.data.RealtimeRepository
import de.rack.app.data.TrainingRepository
import de.rack.app.data.whileForeground
import de.rack.app.domain.HighlightTracker
import de.rack.app.domain.Plan
import de.rack.app.domain.PlanDay
import de.rack.app.domain.PlanExercise
import de.rack.app.domain.RealtimeChange
import de.rack.app.domain.RealtimeEvent
import de.rack.app.domain.SOURCE_AGENT
import de.rack.app.domain.SyncedTable
import de.rack.app.ui.theme.SupersetKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * One renderable entry in a day's exercise list: a group of consecutive
 * exercises that either stand alone ([SupersetKind.NONE]) or share a
 * `superset_id` ([SupersetKind.SUPERSET] / [SupersetKind.CIRCUIT]). Derived
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
 * The grouping context of one logged plan-exercise: its enclosing superset/circuit
 * [group] (the consecutive `superset_id` run, or a singleton) and its [index]
 * within that group. Used to resolve the rest default and the rotation cue on a
 * logged set (docs/specs/spec-timers.md) without re-deriving the grouping in the UI.
 */
data class LoggedExerciseContext(
    val group: List<PlanExercise>,
    val index: Int,
)

/**
 * Resolves the [LoggedExerciseContext] for [planExerciseId] across all on-screen
 * [days]: it locates the exercise within its day's grouped runs and returns that run
 * with the exercise's position in it. Returns null when the id is not on screen.
 */
fun findLoggedExerciseContext(
    days: List<DayContent>,
    planExerciseId: String,
): LoggedExerciseContext? {
    days.forEach { day ->
        day.groups.forEach { group ->
            val index = group.exercises.indexOfFirst { it.id == planExerciseId }
            if (index >= 0) return LoggedExerciseContext(group.exercises, index)
        }
    }
    return null
}

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
 * The launcher actions, bundled so the screen takes one parameter: plan selection,
 * retry, opening a tapped exercise's detail (by catalog exercise id), and starting a
 * guided session for a plan day (by `plan_day_id`). Cross-screen navigation (Home,
 * Verlauf, Artifacts, API-keys, settings, sign-out) lives in the bottom-nav dock, not
 * here.
 */
@Immutable
data class PlanActions(
    val onSelectPlan: (String) -> Unit,
    val onRetry: () -> Unit,
    val onOpenExercise: (String) -> Unit,
    val onStartSession: (String) -> Unit,
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
 * It also consumes the live [RealtimeRepository.events] stream for the
 * `plans`, `plan_days`, and `plan_exercises` tables: an incoming change re-reads
 * the current plan slice (last-write-wins re-read, keeping the join semantics) and,
 * when the payload carries `source='agent'`, flags the touched row for the
 * transient highlight via the [HighlightTracker] (3 s fade owned in that layer).
 *
 * The single live collection is bound to the app lifecycle
 * ([AppLifecycleObserver.whileForeground]): the channel subscribes on foreground and
 * unsubscribes on background, reconnecting on return. Each (re)subscribe carries a
 * [RealtimeEvent.Resync] and this VM re-reads the current plan slice, so an agent
 * change made while disconnected is reflected after re-sync (reconciled as current
 * state, not highlighted).
 */
class PlanViewModel(
    private val repository: TrainingRepository,
    realtime: RealtimeRepository,
    lifecycle: AppLifecycleObserver,
) : ViewModel() {
    private val _uiState = MutableStateFlow<PlanUiState>(PlanUiState.Loading)
    val uiState: StateFlow<PlanUiState> = _uiState.asStateFlow()

    private val highlights = HighlightTracker(viewModelScope)

    init {
        load()
        viewModelScope.launch {
            lifecycle.whileForeground(realtime::events).collect { event ->
                when (event) {
                    RealtimeEvent.Resync -> resyncPlan()
                    is RealtimeEvent.Row -> event.change.takeIf { it.table in PLAN_TABLES }?.let(::reconcileRealtime)
                }
            }
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

    /**
     * Catch-up re-read on every (re)subscribe: re-read the plan list and the current
     * plan's days so an agent change made while disconnected is reflected. The
     * re-read reconciles as current state and is not highlighted — only a live row
     * payload carrying `source='agent'` flags a highlight. No content yet (still
     * loading) needs no re-sync; the initial [load] covers that case.
     */
    private fun resyncPlan() {
        val current = (_uiState.value as? PlanUiState.Content)?.content ?: return
        viewModelScope.launch {
            runCatching { setDays(repository.getPlans(), current.selectedPlanId, highlights.highlighted.value) }
                .onFailure { /* keep the last good content; the next resync re-reads */ }
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
