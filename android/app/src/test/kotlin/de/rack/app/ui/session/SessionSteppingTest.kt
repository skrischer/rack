package de.rack.app.ui.session

import de.rack.app.domain.GroupType
import de.rack.app.domain.PlanExercise
import de.rack.app.ui.theme.SupersetKind
import org.junit.Test
import kotlin.test.assertEquals

/**
 * Covers the pure step-sequence model that drives the guided session player
 * (docs/specs/spec-session-player.md): [buildSessionSteps] orders sets within an
 * exercise and rotates set-by-set within a `superset_id` group, skipping exhausted
 * members on uneven set counts. Each step is identified here by
 * "<exercise>#<setIndex>" for a readable order assertion.
 */
class SessionSteppingTest {
    @Test
    fun `non-superset exercises step straight through all sets then the next`() {
        val steps =
            buildSessionSteps(
                listOf(
                    exercise("a", sets = 2),
                    exercise("b", sets = 3),
                ),
            )

        assertEquals(listOf("a#0", "a#1", "b#0", "b#1", "b#2"), steps.map(::label))
        assertEquals(SupersetKind.NONE, steps.first().kind)
    }

    @Test
    fun `a two-member group rotates set-by-set as a superset`() {
        val steps =
            buildSessionSteps(
                listOf(
                    exercise("a", sets = 3, supersetId = 1, groupType = GroupType.SUPERSET),
                    exercise("b", sets = 3, supersetId = 1, groupType = GroupType.SUPERSET),
                ),
            )

        assertEquals(listOf("a#0", "b#0", "a#1", "b#1", "a#2", "b#2"), steps.map(::label))
        assertEquals(SupersetKind.SUPERSET, steps.first().kind)
    }

    @Test
    fun `a three-plus member group rotates as a circuit`() {
        val steps =
            buildSessionSteps(
                listOf(
                    exercise("a", sets = 2, supersetId = 1, groupType = GroupType.CIRCUIT),
                    exercise("b", sets = 2, supersetId = 1, groupType = GroupType.CIRCUIT),
                    exercise("c", sets = 2, supersetId = 1, groupType = GroupType.CIRCUIT),
                ),
            )

        assertEquals(listOf("a#0", "b#0", "c#0", "a#1", "b#1", "c#1"), steps.map(::label))
        assertEquals(SupersetKind.CIRCUIT, steps.first().kind)
    }

    @Test
    fun `uneven set counts skip the exhausted member and keep rotating the rest`() {
        val steps =
            buildSessionSteps(
                listOf(
                    exercise("a", sets = 4, supersetId = 1, groupType = GroupType.SUPERSET),
                    exercise("b", sets = 3, supersetId = 1, groupType = GroupType.SUPERSET),
                ),
            )

        // Rounds 0-2 rotate both; round 3 has only the 4-set member left.
        assertEquals(listOf("a#0", "b#0", "a#1", "b#1", "a#2", "b#2", "a#3"), steps.map(::label))
    }

    @Test
    fun `exercise blocks keep day order with set counts and a header on each group's first member`() {
        val blocks =
            buildExerciseBlocks(
                listOf(
                    exercise("a", sets = 3),
                    exercise("b", sets = 2, supersetId = 1, groupType = GroupType.SUPERSET),
                    exercise("c", sets = 2, supersetId = 1, groupType = GroupType.SUPERSET),
                ),
            )

        assertEquals(listOf("a", "b", "c"), blocks.map { it.planExerciseId })
        assertEquals(listOf(3, 2, 2), blocks.map { it.setCount })
        // Only the first superset member opens the group header; the standalone never does.
        assertEquals(listOf(false, true, false), blocks.map { it.groupStart })
        assertEquals(SupersetKind.NONE, blocks.first().kind)
        assertEquals(SupersetKind.SUPERSET, blocks[1].kind)
    }

    private fun label(step: SessionStep): String = "${step.exerciseId}#${step.setIndex}"

    private fun exercise(
        id: String,
        sets: Int?,
        supersetId: Int? = null,
        groupType: GroupType? = null,
    ) = PlanExercise(
        id = id,
        dayId = "d",
        exerciseId = id,
        name = id,
        category = null,
        position = 0,
        sets = sets,
        repMin = null,
        repMax = null,
        rirLow = null,
        rirHigh = null,
        restSeconds = null,
        cue = null,
        supersetId = supersetId,
        groupType = groupType,
    )
}
