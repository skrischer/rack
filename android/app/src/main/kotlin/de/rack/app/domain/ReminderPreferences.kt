package de.rack.app.domain

import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalTime
import java.time.ZonedDateTime

/**
 * The device-local workout-reminder preferences (#66, docs/specs/spec-push-notifications.md):
 * whether the reminder is on, which weekdays it fires, and the time of day. These are
 * single-device scheduling prefs with no agent involvement or RLS need, so they live in
 * Android DataStore rather than Supabase. [ReminderRepository][de.rack.app.data.ReminderRepository]
 * persists them; the [WorkoutReminderScheduler][de.rack.app.reminders.WorkoutReminderScheduler]
 * turns them into the next WorkManager run.
 */
data class ReminderPreferences(
    val enabled: Boolean,
    val days: Set<DayOfWeek>,
    val hour: Int,
    val minute: Int,
) {
    companion object {
        /** Off by default with a sensible 18:00 slot, so a fresh install schedules nothing. */
        val DEFAULT = ReminderPreferences(enabled = false, days = emptySet(), hour = 18, minute = 0)
    }
}

/**
 * Pure next-occurrence math for the workout reminder, kept free of Android and WorkManager
 * so it is unit-testable. Given the prefs and the current instant, it returns the delay in
 * milliseconds until the next matching weekday/time, or null when the reminder is off or has
 * no weekday selected (the scheduler then cancels any pending work).
 */
object ReminderSchedule {
    private const val DAYS_PER_WEEK = 7

    fun nextTriggerDelayMillis(
        prefs: ReminderPreferences,
        now: ZonedDateTime,
    ): Long? {
        if (!prefs.enabled || prefs.days.isEmpty()) return null
        val target = LocalTime.of(prefs.hour, prefs.minute)
        return (0..DAYS_PER_WEEK)
            .map { ZonedDateTime.of(now.toLocalDate().plusDays(it.toLong()), target, now.zone) }
            .firstOrNull { it.dayOfWeek in prefs.days && it.isAfter(now) }
            ?.let { Duration.between(now, it).toMillis() }
    }
}
