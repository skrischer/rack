package de.rack.app.domain

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Covers the [currentIsoWeekSets] slice used by the Home overview (Phase 11,
 * docs/specs/spec-dashboards.md): only sets in the same ISO week (Monday start) as
 * `today` are kept, including across year/week boundaries, with order preserved.
 * Fixtures use fixed dates so assertions are clock-independent.
 */
class CurrentIsoWeekSetsTest {
    @Test
    fun `keeps only sets in the same ISO week as today`() {
        // Week of 2026-06-18 is ISO 2026-W25 (Mon 2026-06-15 .. Sun 2026-06-21).
        // The Sun before (06-14) and the Mon after (06-22) fall in adjacent weeks.
        val sets =
            listOf(
                loggedSet(LocalDate.of(2026, 6, 14)),
                loggedSet(LocalDate.of(2026, 6, 15)),
                loggedSet(LocalDate.of(2026, 6, 18)),
                loggedSet(LocalDate.of(2026, 6, 21)),
                loggedSet(LocalDate.of(2026, 6, 22)),
            )

        val result = currentIsoWeekSets(sets, today = LocalDate.of(2026, 6, 18))

        assertEquals(
            listOf(LocalDate.of(2026, 6, 15), LocalDate.of(2026, 6, 18), LocalDate.of(2026, 6, 21)),
            result.map { it.date },
        )
    }

    @Test
    fun `groups the ISO week that straddles the calendar year boundary`() {
        // 2024-12-29 is ISO 2024-W52; 2024-12-30 and 2025-01-01 are both ISO 2025-W01.
        val sets =
            listOf(
                loggedSet(LocalDate.of(2024, 12, 29)),
                loggedSet(LocalDate.of(2024, 12, 30)),
                loggedSet(LocalDate.of(2025, 1, 1)),
            )

        val result = currentIsoWeekSets(sets, today = LocalDate.of(2025, 1, 2))

        assertEquals(
            listOf(LocalDate.of(2024, 12, 30), LocalDate.of(2025, 1, 1)),
            result.map { it.date },
        )
    }

    @Test
    fun `empty input yields empty output`() {
        assertEquals(emptyList(), currentIsoWeekSets(emptyList(), today = LocalDate.of(2026, 6, 18)))
    }

    private var nextId = 0

    private fun loggedSet(date: LocalDate) =
        LoggedSet(
            id = "set-${nextId++}",
            date = date,
            weight = 100.0,
            reps = listOf(5),
            rir = 2,
            exerciseId = "ex",
            exerciseName = "ex",
            muscles = listOf("Chest"),
            category = "chest",
            planDayTag = "push",
            planDayTitle = "Day",
        )
}
