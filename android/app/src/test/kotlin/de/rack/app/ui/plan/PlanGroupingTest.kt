package de.rack.app.ui.plan

import de.rack.app.domain.PlanDay
import de.rack.app.domain.PlanExercise
import de.rack.app.ui.theme.SupersetKind
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Covers the grouping that drives the superset/circuit rendering and the rest +
 * rotation cue lookup (docs/specs/spec-timers.md): [groupExercises] splits a
 * position-ordered list into standalone / superset / circuit runs, and
 * [findLoggedExerciseContext] locates a logged exercise within its on-screen run so
 * the timer can resolve the right rest default and next station.
 */
class PlanGroupingTest {
    @Test
    fun `consecutive equal labels group into one superset or circuit run`() {
        val exercises =
            listOf(
                exercise("a", label = null),
                exercise("b", label = "S1"),
                exercise("c", label = "S1"),
                exercise("d", label = "C1"),
                exercise("e", label = "C1"),
                exercise("f", label = "C1"),
            )

        val groups = groupExercises(exercises)

        assertEquals(listOf(SupersetKind.NONE, SupersetKind.SUPERSET, SupersetKind.CIRCUIT), groups.map { it.kind })
        assertEquals(listOf(1, 2, 3), groups.map { it.exercises.size })
    }

    @Test
    fun `finding a logged exercise returns its run and index across days`() {
        val days =
            listOf(
                day("d1", exercise("a", label = "S1"), exercise("b", label = "S1")),
                day("d2", exercise("c", label = null)),
            )

        val context = findLoggedExerciseContext(days, "b")

        assertEquals(2, context?.group?.size)
        assertEquals(1, context?.index)
    }

    @Test
    fun `finding an off-screen exercise returns null`() {
        val days = listOf(day("d1", exercise("a", label = null)))

        assertNull(findLoggedExerciseContext(days, "missing"))
    }

    private fun day(
        id: String,
        vararg exercises: PlanExercise,
    ) = DayContent(
        day = PlanDay(id = id, planId = "p", position = 1, title = null, focus = null, tag = null),
        groups = groupExercises(exercises.toList()),
    )

    private fun exercise(
        id: String,
        label: String?,
    ) = PlanExercise(
        id = id,
        dayId = "d",
        exerciseId = id,
        name = id,
        category = null,
        position = 0,
        target = null,
        rir = null,
        cue = null,
        supersetLabel = label,
    )
}
