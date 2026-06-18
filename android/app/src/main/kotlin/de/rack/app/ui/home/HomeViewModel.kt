package de.rack.app.ui.home

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.rack.app.data.DashboardRepository
import de.rack.app.domain.LoggedSet
import de.rack.app.domain.SessionSummary
import de.rack.app.domain.StreakStats
import de.rack.app.domain.VolumeTotals
import de.rack.app.domain.WeeklyVolume
import de.rack.app.domain.currentIsoWeekSets
import de.rack.app.domain.sessionSummaries
import de.rack.app.domain.streakStats
import de.rack.app.domain.weeklyVolume
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * The Home overview's loaded content (docs/specs/spec-dashboards.md, Phase 11):
 * the current ISO week's volume breakdown, the streak stats, and the recent
 * sessions (newest first, capped at [RECENT_SESSIONS_LIMIT]). All derived from the
 * pure metric functions over the user's logged sets.
 */
@Immutable
data class HomeContent(
    val weeklyVolume: WeeklyVolume,
    val weekTotals: VolumeTotals,
    val streak: StreakStats,
    val recentSessions: List<SessionSummary>,
)

/** Home overview UI state: loading, empty (no logged sets), loaded content, or error. */
sealed interface HomeUiState {
    data object Loading : HomeUiState

    /** The signed-in user has no logged sets yet (fresh install / RLS-empty). */
    data object Empty : HomeUiState

    data class Content(val content: HomeContent) : HomeUiState

    data class Error(val message: String) : HomeUiState
}

/**
 * Reads the signed-in user's logged sets through the [DashboardRepository] (all under
 * RLS — no `user_id` is ever sent) and exposes the Home overview metrics as a
 * [StateFlow]. Aggregation is delegated to the pure functions in
 * [de.rack.app.domain.DashboardMetrics]; this ViewModel only orchestrates the fetch,
 * the today-relative slicing, and the loading/empty/loaded/error state.
 *
 * [refresh] re-reads in place so the screen can refresh on resume without dropping to
 * the loading spinner. No Supabase access happens in Composables; the screen observes
 * [uiState] and emits events only.
 */
class HomeViewModel(
    private val repository: DashboardRepository,
    private val today: () -> LocalDate = LocalDate::now,
) : ViewModel() {
    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    /** (Re)load the overview, showing the full-screen spinner. */
    fun load() {
        _uiState.value = HomeUiState.Loading
        viewModelScope.launch { fetch() }
    }

    /** Re-read in place (used on screen resume), keeping the current content visible. */
    fun refresh() {
        viewModelScope.launch { fetch() }
    }

    private suspend fun fetch() {
        runCatching { repository.getLoggedSets() }
            .onSuccess { sets -> _uiState.value = stateFor(sets) }
            .onFailure { error -> _uiState.value = HomeUiState.Error(messageFor(error)) }
    }

    private fun stateFor(sets: List<LoggedSet>): HomeUiState {
        if (sets.isEmpty()) return HomeUiState.Empty
        val now = today()
        val weekSets = currentIsoWeekSets(sets, now)
        val weekly = weeklyVolume(weekSets)
        return HomeUiState.Content(
            HomeContent(
                weeklyVolume = weekly,
                weekTotals = weekly.totals,
                streak = streakStats(sets, now),
                recentSessions = sessionSummaries(sets, limit = RECENT_SESSIONS_LIMIT),
            ),
        )
    }

    private fun messageFor(error: Throwable): String = error.message?.takeIf { it.isNotBlank() } ?: GENERIC_ERROR

    private companion object {
        /** Home shows the last 10 sessions; the full record lives in calendar/history (spec). */
        const val RECENT_SESSIONS_LIMIT = 10
        const val GENERIC_ERROR = "Could not load your overview. Check your connection and try again."
    }
}
