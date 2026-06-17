package de.rack.app.ui.session

import de.rack.app.domain.PlanExercise
import de.rack.app.ui.theme.SupersetKind
import org.junit.Test
import kotlin.test.assertEquals

/**
 * Covers the pure step-sequence model that drives the guided session player
 * (docs/specs/spec-session-player.md): [buildSessionSteps] orders sets within an
 * exercise and rotates set-by-set within a `superset_label` group, skipping
 * exhausted members on uneven set counts. Each step is identified here by
 * "<exercise>#<setIndex>" for a readable order assertion.
 */
class SessionSteppingTest {
    @Test
    fun `non-superset exercises step straight through all sets then the next`() {
        val steps =
            buildSessionSteps(
                listOf(
                    exercise("a", target = "2 x 5", label = null),
                    exercise("b", target = "3 x 8", label = null),
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
                    exercise("a", target = "3 x 10", label = "S1"),
                    exercise("b", target = "3 x 10", label = "S1"),
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
                    exercise("a", target = "2 x 12", label = "C1"),
                    exercise("b", target = "2 x 12", label = "C1"),
                    exercise("c", target = "2 x 12", label = "C1"),
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
                    exercise("a", target = "4 x 5", label = "S1"),
                    exercise("b", target = "3 x 8", label = "S1"),
                ),
            )

        // Rounds 0-2 rotate both; round 3 has only the 4-set member left.
        assertEquals(listOf("a#0", "b#0", "a#1", "b#1", "a#2", "b#2", "a#3"), steps.map(::label))
    }

    private fun label(step: SessionStep): String = "${step.exerciseId}#${step.setIndex}"

    private fun exercise(
        id: String,
        target: String?,
        label: String?,
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
        supersetLabel = label,
    )
}
