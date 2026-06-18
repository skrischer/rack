package de.rack.app.domain

import org.junit.Test
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Coverage for the pure next-occurrence math behind the local workout reminder (#66,
 * docs/specs/spec-push-notifications.md). Kept free of Android/WorkManager so it runs as a
 * fast JVM test: it pins a fixed "now" and asserts the computed delay to the next matching
 * weekday/time, including the same-day-later, already-passed-roll-to-next-week, and
 * disabled/no-weekday cases.
 */
class ReminderScheduleTest {
    private val zone = ZoneId.of("Europe/Berlin")

    // A Wednesday at 10:00 local.
    private val now: ZonedDateTime =
        ZonedDateTime.of(LocalDateTime.of(2026, 6, 17, 10, 0), zone)

    @Test
    fun `returns null when disabled`() {
        val prefs = ReminderPreferences(enabled = false, days = setOf(DayOfWeek.WEDNESDAY), hour = 18, minute = 0)

        assertNull(ReminderSchedule.nextTriggerDelayMillis(prefs, now))
    }

    @Test
    fun `returns null when no weekday is selected`() {
        val prefs = ReminderPreferences(enabled = true, days = emptySet(), hour = 18, minute = 0)

        assertNull(ReminderSchedule.nextTriggerDelayMillis(prefs, now))
    }

    @Test
    fun `fires later the same day when the time is still ahead`() {
        val prefs = ReminderPreferences(enabled = true, days = setOf(DayOfWeek.WEDNESDAY), hour = 18, minute = 30)

        val delay = ReminderSchedule.nextTriggerDelayMillis(prefs, now)

        assertEquals(Duration.ofHours(8).plusMinutes(30).toMillis(), delay)
    }

    @Test
    fun `rolls to next week when the only weekday already passed today`() {
        // 09:00 is before the 10:00 "now", so today's slot is gone -> next Wednesday.
        val prefs = ReminderPreferences(enabled = true, days = setOf(DayOfWeek.WEDNESDAY), hour = 9, minute = 0)

        val delay = ReminderSchedule.nextTriggerDelayMillis(prefs, now)

        assertEquals(Duration.ofDays(7).minusHours(1).toMillis(), delay)
    }

    @Test
    fun `picks the nearest of several selected weekdays`() {
        // From Wednesday 10:00, the next Friday 08:00 is sooner than Monday.
        val prefs =
            ReminderPreferences(
                enabled = true,
                days = setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY),
                hour = 8,
                minute = 0,
            )

        val delay = ReminderSchedule.nextTriggerDelayMillis(prefs, now)

        assertEquals(Duration.ofDays(1).plusHours(22).toMillis(), delay)
    }
}
