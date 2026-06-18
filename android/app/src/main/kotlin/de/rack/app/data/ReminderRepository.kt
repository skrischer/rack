package de.rack.app.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import de.rack.app.domain.ReminderPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.DayOfWeek

/**
 * The single access point for the device-local workout-reminder preferences (#66,
 * docs/specs/spec-push-notifications.md), persisted via Jetpack DataStore so they survive
 * an app restart. These are derived single-device scheduling prefs with no agent
 * involvement, so they never touch Supabase — no table, no migration, no MCP tool.
 *
 * Weekdays are stored as the set of ISO day numbers (1 = Monday .. 7 = Sunday). Missing keys
 * fall back to [ReminderPreferences.DEFAULT] (off), so a first read on a fresh install yields
 * the defaults. No Composable reads DataStore directly; the reminder ViewModel consumes this.
 */
class ReminderRepository(
    private val dataStore: DataStore<Preferences>,
) {
    /** The persisted preferences, defaulting to [ReminderPreferences.DEFAULT] when unset. */
    val preferences: Flow<ReminderPreferences> = dataStore.data.map(::toPreferences)

    /** Persist [prefs], replacing the enabled flag, weekday set, and time of day. */
    suspend fun save(prefs: ReminderPreferences) {
        dataStore.edit { store ->
            store[ENABLED_KEY] = prefs.enabled
            store[DAYS_KEY] = prefs.days.map { it.value.toString() }.toSet()
            store[HOUR_KEY] = prefs.hour
            store[MINUTE_KEY] = prefs.minute
        }
    }

    private fun toPreferences(store: Preferences): ReminderPreferences =
        ReminderPreferences(
            enabled = store[ENABLED_KEY] ?: ReminderPreferences.DEFAULT.enabled,
            days = store[DAYS_KEY]?.let(::decodeDays) ?: ReminderPreferences.DEFAULT.days,
            hour = store[HOUR_KEY] ?: ReminderPreferences.DEFAULT.hour,
            minute = store[MINUTE_KEY] ?: ReminderPreferences.DEFAULT.minute,
        )

    private fun decodeDays(stored: Set<String>): Set<DayOfWeek> =
        stored.mapNotNull { it.toIntOrNull()?.let(DayOfWeek::of) }.toSet()

    private companion object {
        val ENABLED_KEY = booleanPreferencesKey("reminder_enabled")
        val DAYS_KEY = stringSetPreferencesKey("reminder_days")
        val HOUR_KEY = intPreferencesKey("reminder_hour")
        val MINUTE_KEY = intPreferencesKey("reminder_minute")
    }
}
