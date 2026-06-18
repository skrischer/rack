package de.rack.app.ui.calendar

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.rack.app.data.DashboardRepository
import de.rack.app.domain.HistoryRange
import de.rack.app.domain.LoggedExerciseEntry
import de.rack.app.domain.LoggedSet
import de.rack.app.domain.loggedDates
import de.rack.app.domain.loggedHistoryRange
import de.rack.app.domain.sessionDetail
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

/**
 * The calendar/history loaded content (docs/specs/spec-dashboards.md, Phase 11): the
 * marked logged dates, the navigable month range over the full history, the currently
 * shown [month], and the [selectedDate] with its expanded logged exercises. The screen
 * marks exactly [loggedDates] and discloses [selectedDetail] for a tapped marked day.
 */
@Immutable
data class CalendarContent(
    val loggedDates: Set<LocalDate>,
    val range: HistoryRange,
    val month: YearMonth,
    val selectedDate: LocalDate?,
    val selectedDetail: List<LoggedExerciseEntry>,
)

/** Calendar/history UI state: loading, empty (no logged sets), loaded content, or error. */
sealed interface CalendarUiState {
    data object Loading : CalendarUiState

    /** The signed-in user has no logged sets yet (fresh install / RLS-empty). */
    data object Empty : CalendarUiState

    data class Content(val content: CalendarContent) : CalendarUiState

    data class Error(val message: String) : CalendarUiState
}

/**
 * Reads the signed-in user's logged sets through the [DashboardRepository] (under RLS —
 * no `user_id` is ever sent) and exposes the calendar/history state as a [StateFlow].
 * Marking, range, and per-day detail are delegated to the pure functions in
 * [de.rack.app.domain.CalendarHistory]; this ViewModel only orchestrates the fetch,
 * the visible-month and selected-day navigation, and the loading/empty/loaded/error
 * state. No Supabase access happens in Composables.
 *
 * [initialDate], when non-null, is the Home recent-session deep link: the calendar opens
 * on that month with that day selected and expanded.
 */
class CalendarViewModel(
    private val repository: DashboardRepository,
    private val initialDate: LocalDate? = null,
    private val today: () -> LocalDate = LocalDate::now,
) : ViewModel() {
    private val _uiState = MutableStateFlow<CalendarUiState>(CalendarUiState.Loading)
    val uiState: StateFlow<CalendarUiState> = _uiState.asStateFlow()

    private var sets: List<LoggedSet> = emptyList()

    init {
        load()
    }

    /** (Re)load the history, showing the full-screen spinner. */
    fun load() {
        _uiState.value = CalendarUiState.Loading
        viewModelScope.launch { fetch() }
    }

    /** Re-read in place (used on screen resume), keeping the shown month/selection. */
    fun refresh() {
        viewModelScope.launch { fetch() }
    }

    /** Step the visible month within the logged history range. */
    fun showMonth(month: YearMonth) {
        updateContent { it.copy(month = month) }
    }

    /** Select a day; a day with logged sets expands its exercises, a bare day clears it. */
    fun selectDate(date: LocalDate) {
        updateContent { content ->
            content.copy(selectedDate = date, selectedDetail = sessionDetail(sets, date))
        }
    }

    private suspend fun fetch() {
        runCatching { repository.getLoggedSets() }
            .onSuccess { loaded ->
                sets = loaded
                _uiState.value = stateFor(loaded)
            }
            .onFailure { error -> _uiState.value = CalendarUiState.Error(messageFor(error)) }
    }

    private fun stateFor(loaded: List<LoggedSet>): CalendarUiState {
        val range = loggedHistoryRange(loaded) ?: return CalendarUiState.Empty
        val current = (_uiState.value as? CalendarUiState.Content)?.content
        val selected = current?.selectedDate ?: initialDate
        val month = current?.month ?: YearMonth.from(selected ?: today()).coerceIn(range)
        return CalendarUiState.Content(
            CalendarContent(
                loggedDates = loggedDates(loaded),
                range = range,
                month = month,
                selectedDate = selected,
                selectedDetail = selected?.let { sessionDetail(loaded, it) }.orEmpty(),
            ),
        )
    }

    private fun updateContent(transform: (CalendarContent) -> CalendarContent) {
        val content = (_uiState.value as? CalendarUiState.Content)?.content ?: return
        _uiState.value = CalendarUiState.Content(transform(content))
    }

    private fun messageFor(error: Throwable): String = error.message?.takeIf { it.isNotBlank() } ?: GENERIC_ERROR

    private companion object {
        const val GENERIC_ERROR = "Could not load your history. Check your connection and try again."
    }
}

/** Clamp a month into the logged history range so navigation never leaves the data span. */
private fun YearMonth.coerceIn(range: HistoryRange): YearMonth =
    when {
        isBefore(range.earliest) -> range.earliest
        isAfter(range.latest) -> range.latest
        else -> this
    }
