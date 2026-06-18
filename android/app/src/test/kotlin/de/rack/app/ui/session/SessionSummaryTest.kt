package de.rack.app.ui.session

import de.rack.app.domain.PlanExercise
import de.rack.app.ui.theme.SupersetKind
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Covers the pure session-summary aggregation that drives the confirm-save screen
 * (issue #60, docs/specs/spec-session-player.md): [buildSessionSummary] counts ticked
 * sets vs. target, computes volume = per-exercise weight x sum(ticked reps), and omits
 * skipped exercises (no reps and no weight); [loggedReps] gathers only the positive reps
 * of the ticked sets in set order.
 */
class SessionSummaryTest {
    @Test
    fun `summary counts ticked sets and volume from per-exercise weight times reps`() {
        val a = exercise("a", target = "3 x 8")
        val done = listOf(step("a", 0), step("a", 1))
        val entries = mapOf("a" to ExerciseEntries(weight = "60", rir = "1", reps = mapOf(0 to "8", 1 to "6")))

        val summary = buildSessionSummary(listOf(a), done, entries)

        val line = summary.lines.single()
        assertEquals(2, line.setsDone)
        assertEquals(3, line.targetSets)
        assertEquals(60.0 * (8 + 6), line.volume, 0.0)
        assertEquals(60.0 * (8 + 6), summary.totalVolume, 0.0)
    }

    @Test
    fun `an exercise with no reps and no weight is skipped from the summary`() {
        val a = exercise("a", target = "3 x 8")
        val b = exercise("b", target = "3 x 8")
        // a is ticked with reps; b is ticked but left blank (no reps, no weight).
        val done = listOf(step("a", 0), step("b", 0))
        val entries =
            mapOf(
                "a" to ExerciseEntries(weight = "40", reps = mapOf(0 to "10")),
                "b" to ExerciseEntries(weight = "", reps = mapOf(0 to "")),
            )

        val summary = buildSessionSummary(listOf(a, b), done, entries)

        assertEquals(listOf("a"), summary.lines.map { it.planExerciseId })
    }

    @Test
    fun `a fully empty session yields an empty summary`() {
        val a = exercise("a", target = "3 x 8")

        val summary = buildSessionSummary(listOf(a), done = emptyList(), entries = emptyMap())

        assertTrue(summary.isEmpty)
        assertEquals(0.0, summary.totalVolume, 0.0)
    }

    @Test
    fun `weight-only ticked set keeps the exercise but logs no reps`() {
        val a = exercise("a", target = "2 x 5")
        val done = listOf(step("a", 0))
        val entries = mapOf("a" to ExerciseEntries(weight = "100", reps = mapOf(0 to "0")))

        val summary = buildSessionSummary(listOf(a), done, entries)

        val line = summary.lines.single()
        assertEquals(0, line.setsDone)
        assertEquals(0.0, line.volume, 0.0)
    }

    @Test
    fun `logged reps takes only positive ticked sets in set order`() {
        val done = listOf(step("a", 2), step("a", 0))
        val entries = ExerciseEntries(reps = mapOf(0 to "8", 1 to "7", 2 to "6"))

        // Only sets 0 and 2 were ticked; result is ordered by set index.
        assertEquals(listOf(8, 6), loggedReps("a", done, entries))
    }

    private fun step(
        id: String,
        setIndex: Int,
    ) = SessionStep(
        planExerciseId = id,
        exerciseId = id,
        name = id,
        kind = SupersetKind.NONE,
        setIndex = setIndex,
        totalSets = 3,
    )

    private fun exercise(
        id: String,
        target: String?,
    ) = PlanExercise(
        id = id,
        dayId = "d",
        exerciseId = id,
        name = id,
        category = null,
        position = 0,
        target = target,
        rir = null,
        cue = null,
        supersetLabel = null,
    )
}
