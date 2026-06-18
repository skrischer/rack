package de.rack.app.domain

import java.time.LocalDate
import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Covers the pure calendar/history aggregation (Phase 11, docs/specs/spec-dashboards.md):
 * the set of marked days, the navigable month range over the full logged history, and the
 * per-day exercise detail (weight + per-set reps + RIR). Fixtures use fixed dates so the
 * assertions are clock-independent.
 */
class CalendarHistoryTest {
    @Test
    fun `logged dates are the distinct dates that have sets`() {
        val sets =
            listOf(
                loggedSet(date = LocalDate.of(2026, 6, 1)),
                loggedSet(date = LocalDate.of(2026, 6, 1)),
                loggedSet(date = LocalDate.of(2026, 6, 8)),
            )

        assertEquals(
            setOf(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 8)),
            loggedDates(sets),
        )
    }

    @Test
    fun `logged dates over no sets is empty`() {
        assertTrue(loggedDates(emptyList()).isEmpty())
    }

    @Test
    fun `history range spans the earliest to latest logged month`() {
        val sets =
            listOf(
                loggedSet(date = LocalDate.of(2026, 1, 20)),
                loggedSet(date = LocalDate.of(2026, 6, 8)),
                loggedSet(date = LocalDate.of(2026, 3, 2)),
            )

        val range = loggedHistoryRange(sets)

        assertEquals(YearMonth.of(2026, 1), range?.earliest)
        assertEquals(YearMonth.of(2026, 6), range?.latest)
    }

    @Test
    fun `history range over no sets is null`() {
        assertNull(loggedHistoryRange(emptyList()))
    }

    @Test
    fun `session detail groups a day's sets by exercise with weight reps and rir`() {
        val day = LocalDate.of(2026, 6, 8)
        val sets =
            listOf(
                loggedSet(date = day, weight = 100.0, reps = listOf(5, 5), rir = 2)
                    .copy(exerciseId = "bench", exerciseName = "Bench Press"),
                loggedSet(date = day, weight = 105.0, reps = listOf(3), rir = 1)
                    .copy(exerciseId = "bench", exerciseName = "Bench Press"),
                loggedSet(date = day, weight = null, reps = listOf(10), rir = null)
                    .copy(exerciseId = "pullup", exerciseName = "Pull Up"),
                loggedSet(date = LocalDate.of(2026, 6, 9), weight = 50.0, reps = listOf(8))
                    .copy(exerciseId = "curl", exerciseName = "Curl"),
            )

        val detail = sessionDetail(sets, day)

        assertEquals(listOf("Bench Press", "Pull Up"), detail.map { it.exerciseName })
        val bench = detail.first()
        assertEquals(2, bench.sets.size)
        assertEquals(LoggedSetEntry(weight = 100.0, reps = listOf(5, 5), rir = 2), bench.sets[0])
        assertEquals(LoggedSetEntry(weight = 105.0, reps = listOf(3), rir = 1), bench.sets[1])
        val pullup = detail[1]
        assertEquals(LoggedSetEntry(weight = null, reps = listOf(10), rir = null), pullup.sets.single())
    }

    @Test
    fun `session detail for a day with no logs is empty`() {
        val sets = listOf(loggedSet(date = LocalDate.of(2026, 6, 8)))

        assertTrue(sessionDetail(sets, LocalDate.of(2026, 6, 9)).isEmpty())
    }

    private var nextId = 0

    private fun loggedSet(
        date: LocalDate = LocalDate.of(2026, 6, 15),
        weight: Double? = 100.0,
        reps: List<Int> = listOf(5),
        rir: Int? = 2,
    ) = LoggedSet(
        id = "set-${nextId++}",
        date = date,
        weight = weight,
        reps = reps,
        rir = rir,
        exerciseId = "ex",
        exerciseName = "ex",
        muscles = listOf("Chest"),
        category = "chest",
        planDayTag = "push",
        planDayTitle = "Day",
    )
}
