package de.rack.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.rack.app.data.ReminderRepository
import de.rack.app.domain.ReminderPreferences
import de.rack.app.reminders.WorkoutReminderScheduler
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek

/**
 * Exposes the device-local [ReminderPreferences] as a [StateFlow] and mediates every edit
 * through the [ReminderRepository] and [WorkoutReminderScheduler] (#66,
 * docs/specs/spec-push-notifications.md). Each intent applies the change to the current prefs,
 * persists it to DataStore, and re-arms WorkManager from the new prefs, so toggling the reminder
 * or editing the days/time immediately reflects in the next scheduled run.
 *
 * All persistence and scheduling stay out of the Composable; the section observes [uiState] only.
 */
class ReminderViewModel(
    private val repository: ReminderRepository,
    private val scheduler: WorkoutReminderScheduler,
) : ViewModel() {
    val uiState: StateFlow<ReminderPreferences> =
        repository.preferences.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = ReminderPreferences.DEFAULT,
        )

    /** Turn the reminder on/off; off cancels the pending WorkManager run. */
    fun setEnabled(enabled: Boolean) = update { it.copy(enabled = enabled) }

    /** Add or remove [day] from the weekday set that the reminder fires on. */
    fun toggleDay(day: DayOfWeek) =
        update { prefs ->
            val days = if (day in prefs.days) prefs.days - day else prefs.days + day
            prefs.copy(days = days)
        }

    /** Set the time of day the reminder fires. */
    fun setTime(
        hour: Int,
        minute: Int,
    ) = update { it.copy(hour = hour, minute = minute) }

    private fun update(change: (ReminderPreferences) -> ReminderPreferences) {
        val next = change(uiState.value)
        viewModelScope.launch {
            repository.save(next)
            scheduler.reschedule(next)
        }
    }
}
