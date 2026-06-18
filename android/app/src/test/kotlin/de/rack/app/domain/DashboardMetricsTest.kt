package de.rack.app.domain

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Covers the pure dashboard aggregation (docs/specs/spec-dashboards.md, Phase 11):
 * weekly volume per muscle/tag with the Other/untagged buckets, working-set counts
 * with weight-null sets counting as a set but zero volume, the ISO-week streak
 * across year and week boundaries, the date-keyed session list, and per-exercise
 * top-set/volume progress. All fixtures use fixed dates so assertions are clock-
 * independent.
 */
class DashboardMetricsTest {
    @Test
    fun `volume is sum of weight times each rep and zero for weight-null sets`() {
        val weighted = loggedSet(weight = 100.0, reps = listOf(5, 5))
        val bodyweight = loggedSet(weight = null, reps = listOf(10, 10))

        assertEquals(1000.0, weighted.volume)
        assertEquals(0.0, bodyweight.volume)
    }

    @Test
    fun `weekly volume credits each listed muscle fully and totals once`() {
        val sets =
            listOf(
                loggedSet(weight = 100.0, reps = listOf(5), muscles = listOf("Chest", "Triceps")),
                loggedSet(weight = 50.0, reps = listOf(10), muscles = listOf("Chest")),
            )

        val result = weeklyVolume(sets)

        // Chest is on both sets (500 + 500), Triceps only on the first (500).
        assertEquals(1000.0, result.perMuscle.getValue("Chest").volume)
        assertEquals(500.0, result.perMuscle.getValue("Triceps").volume)
        // The set is counted once toward the overall totals, not once per muscle.
        assertEquals(1000.0, result.totals.volume)
        assertEquals(2, result.totals.workingSets)
    }

    @Test
    fun `weekly volume buckets missing muscles as Other and missing tag as untagged`() {
        val sets =
            listOf(
                loggedSet(weight = 80.0, reps = listOf(5), muscles = emptyList(), tag = null),
                loggedSet(weight = 60.0, reps = listOf(8), muscles = listOf("Back"), tag = "pull"),
            )

        val result = weeklyVolume(sets)

        assertEquals(400.0, result.perMuscle.getValue(OTHER_MUSCLE).volume)
        assertEquals(480.0, result.perMuscle.getValue("Back").volume)
        assertEquals(400.0, result.perTag.getValue(UNTAGGED_TAG).volume)
        assertEquals(480.0, result.perTag.getValue("pull").volume)
    }

    @Test
    fun `weekly volume counts a weight-null set as a working set with zero volume`() {
        val sets = listOf(loggedSet(weight = null, reps = listOf(15), muscles = listOf("Abs"), tag = "core"))

        val result = weeklyVolume(sets)

        assertEquals(1, result.perMuscle.getValue("Abs").workingSets)
        assertEquals(0.0, result.perMuscle.getValue("Abs").volume)
        assertEquals(1, result.totals.workingSets)
    }

    @Test
    fun `streak counts consecutive ISO weeks ending this week`() {
        // Three consecutive Mondays ending the week of 2026-06-15.
        val sets =
            listOf(
                loggedSet(date = LocalDate.of(2026, 6, 1)),
                loggedSet(date = LocalDate.of(2026, 6, 8)),
                loggedSet(date = LocalDate.of(2026, 6, 15)),
            )

        val stats = streakStats(sets, today = LocalDate.of(2026, 6, 18))

        assertEquals(3, stats.current)
        assertEquals(3, stats.longest)
    }

    @Test
    fun `a gap breaks the current streak but longest keeps the best run`() {
        val sets =
            listOf(
                loggedSet(date = LocalDate.of(2026, 5, 4)),
                loggedSet(date = LocalDate.of(2026, 5, 11)),
                loggedSet(date = LocalDate.of(2026, 5, 18)),
                // skip the week of 2026-05-25
                loggedSet(date = LocalDate.of(2026, 6, 8)),
                loggedSet(date = LocalDate.of(2026, 6, 15)),
            )

        val stats = streakStats(sets, today = LocalDate.of(2026, 6, 18))

        assertEquals(2, stats.current)
        assertEquals(3, stats.longest)
    }

    @Test
    fun `current streak is zero when this week has no session`() {
        val sets =
            listOf(
                loggedSet(date = LocalDate.of(2026, 6, 1)),
                loggedSet(date = LocalDate.of(2026, 6, 8)),
            )

        val stats = streakStats(sets, today = LocalDate.of(2026, 6, 22))

        assertEquals(0, stats.current)
        assertEquals(2, stats.longest)
    }

    @Test
    fun `streak spans the year boundary by ISO week not calendar year`() {
        // ISO weeks 2024-W52 (Dec 23), 2025-W01 (Dec 30, 2024) and 2025-W02 (Jan 6,
        // 2025) are consecutive even though they straddle the calendar-year change.
        val sets =
            listOf(
                loggedSet(date = LocalDate.of(2024, 12, 23)),
                loggedSet(date = LocalDate.of(2024, 12, 30)),
                loggedSet(date = LocalDate.of(2025, 1, 6)),
            )

        val stats = streakStats(sets, today = LocalDate.of(2025, 1, 8))

        assertEquals(3, stats.current)
        assertEquals(3, stats.longest)
    }

    @Test
    fun `same ISO week from two calendar years does not double-count`() {
        // 2024-12-30 and 2024-12-31 both fall in ISO week 2025-W01; one logged week.
        val sets =
            listOf(
                loggedSet(date = LocalDate.of(2024, 12, 30)),
                loggedSet(date = LocalDate.of(2024, 12, 31)),
            )

        val stats = streakStats(sets, today = LocalDate.of(2025, 1, 1))

        assertEquals(1, stats.current)
        assertEquals(1, stats.longest)
    }

    @Test
    fun `empty history yields a zero streak`() {
        val stats = streakStats(emptyList(), today = LocalDate.of(2026, 6, 18))

        assertEquals(0, stats.current)
        assertEquals(0, stats.longest)
    }

    @Test
    fun `sessions group by date newest first with per-session totals`() {
        val jun1 = LocalDate.of(2026, 6, 1)
        val jun3 = LocalDate.of(2026, 6, 3)
        val sets =
            listOf(
                loggedSet(date = jun1, weight = 100.0, reps = listOf(5), tag = "push").copy(planDayTitle = "Push"),
                loggedSet(date = jun1, weight = 50.0, reps = listOf(10), tag = "push").copy(planDayTitle = "Push"),
                loggedSet(date = jun3, weight = 80.0, reps = listOf(8), tag = "pull").copy(planDayTitle = "Pull"),
            )

        val sessions = sessionSummaries(sets)

        assertEquals(listOf(jun3, jun1), sessions.map { it.date })
        assertEquals("Pull", sessions[0].title)
        assertEquals(2, sessions[1].workingSets)
        assertEquals(1000.0, sessions[1].volume)
    }

    @Test
    fun `session summaries respect the limit cap`() {
        val sets = (1..15).map { day -> loggedSet(date = LocalDate.of(2026, 6, day)) }

        val capped = sessionSummaries(sets, limit = 10)

        assertEquals(10, capped.size)
        assertEquals(LocalDate.of(2026, 6, 15), capped.first().date)
    }

    @Test
    fun `exercise progress reports top set weight and volume per date oldest first`() {
        val jun1 = LocalDate.of(2026, 6, 1)
        val jun3 = LocalDate.of(2026, 6, 3)
        val sets =
            listOf(
                loggedSet(date = jun3, weight = 90.0, reps = listOf(5)).copy(exerciseId = "bench"),
                loggedSet(date = jun3, weight = 100.0, reps = listOf(3)).copy(exerciseId = "bench"),
                loggedSet(date = jun1, weight = 80.0, reps = listOf(8)).copy(exerciseId = "bench"),
                loggedSet(date = LocalDate.of(2026, 6, 2), weight = 120.0, reps = listOf(5)).copy(exerciseId = "squat"),
            )

        val progress = exerciseProgress(sets, exerciseId = "bench")

        assertEquals(listOf(jun1, jun3), progress.map { it.date })
        assertEquals(80.0, progress[0].topSetWeight)
        assertEquals(100.0, progress[1].topSetWeight)
        // jun3 bench: 90*5 + 100*3 = 450 + 300.
        assertEquals(750.0, progress[1].volume)
    }

    @Test
    fun `exercise progress for a bodyweight-only date has null top set and zero volume`() {
        val bodyweight =
            loggedSet(date = LocalDate.of(2026, 6, 1), weight = null, reps = listOf(10))
                .copy(exerciseId = "pullup")

        val progress = exerciseProgress(listOf(bodyweight), exerciseId = "pullup")

        assertEquals(1, progress.size)
        assertNull(progress[0].topSetWeight)
        assertEquals(0.0, progress[0].volume)
    }

    @Test
    fun `weekly volume over no sets is empty`() {
        val result = weeklyVolume(emptyList())

        assertTrue(result.perMuscle.isEmpty())
        assertTrue(result.perTag.isEmpty())
        assertEquals(0, result.totals.workingSets)
        assertEquals(0.0, result.totals.volume)
    }

    private var nextId = 0

    private fun loggedSet(
        date: LocalDate = LocalDate.of(2026, 6, 15),
        weight: Double? = 100.0,
        reps: List<Int> = listOf(5),
        muscles: List<String> = listOf("Chest"),
        tag: String? = "push",
    ) = LoggedSet(
        id = "set-${nextId++}",
        date = date,
        weight = weight,
        reps = reps,
        rir = 2,
        exerciseId = "ex",
        exerciseName = "ex",
        muscles = muscles,
        category = "chest",
        planDayTag = tag,
        planDayTitle = "Day",
    )
}
