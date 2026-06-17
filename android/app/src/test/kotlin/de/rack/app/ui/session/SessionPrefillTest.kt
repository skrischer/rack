package de.rack.app.ui.session

import de.rack.app.domain.PlanExercise
import de.rack.app.domain.SetLog
import de.rack.app.ui.theme.SupersetKind
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Covers the pure session-player display logic the screen renders (issue #58,
 * docs/specs/spec-session-player.md): [prefillEntries] seeds kg/RIR/reps from the
 * last log and the target, and [SessionPlayerUiState.rotationCueName] surfaces the
 * "Next: <exercise>" hand-off only inside a superset/circuit group. kg and RIR are
 * single per-exercise values; only reps are per-set.
 */
class SessionPrefillTest {
    @Test
    fun `prefill takes kg and rir from the last log and reps per set`() {
        val exercise = exercise("a", target = "3 x 8", rir = 1, label = null)
        val last = log("a", weight = 60.0, reps = listOf(8, 7, 6), rir = 2)

        val entries = prefillEntries(listOf(exercise), mapOf("a" to last)).getValue("a")

        assertEquals("60", entries.weight)
        assertEquals("2", entries.rir)
        assertEquals(mapOf(0 to "8", 1 to "7", 2 to "6"), entries.reps)
    }

    @Test
    fun `prefill falls back to target rir and target reps with no last log`() {
        val exercise = exercise("a", target = "4 x 5-8", rir = 2, label = null)

        val entries = prefillEntries(listOf(exercise), emptyMap()).getValue("a")

        assertEquals("", entries.weight)
        assertEquals("2", entries.rir)
        assertEquals(mapOf(0 to "5-8", 1 to "5-8", 2 to "5-8", 3 to "5-8"), entries.reps)
    }

    @Test
    fun `rotation cue names the next group member but not a same-exercise step`() {
        val a0 = step("a", kind = SupersetKind.SUPERSET, setIndex = 0)
        val b0 = step("b", kind = SupersetKind.SUPERSET, setIndex = 0)
        val a1 = step("a", kind = SupersetKind.SUPERSET, setIndex = 1)

        // a -> b is a real hand-off; a -> a (same exercise, next round) is not.
        assertEquals("b", SessionPlayerUiState(focused = a0, remaining = listOf(b0, a1)).rotationCueName)
        assertNull(SessionPlayerUiState(focused = a0, remaining = listOf(a1)).rotationCueName)
    }

    @Test
    fun `rotation cue is absent for a standalone exercise`() {
        val a0 = step("a", kind = SupersetKind.NONE, setIndex = 0)
        val a1 = step("a", kind = SupersetKind.NONE, setIndex = 1)

        assertNull(SessionPlayerUiState(focused = a0, remaining = listOf(a1)).rotationCueName)
    }

    private fun step(
        id: String,
        kind: SupersetKind,
        setIndex: Int,
    ) = SessionStep(
        planExerciseId = id,
        exerciseId = id,
        name = id,
        kind = kind,
        setIndex = setIndex,
        totalSets = 3,
    )

    private fun exercise(
        id: String,
        target: String?,
        rir: Int?,
        label: String?,
    ) = PlanExercise(
        id = id,
        dayId = "d",
        exerciseId = id,
        name = id,
        category = null,
        position = 0,
        target = target,
        rir = rir,
        cue = null,
        supersetLabel = label,
    )

    private fun log(
        planExerciseId: String,
        weight: Double?,
        reps: List<Int>,
        rir: Int?,
    ) = SetLog(
        id = "l-$planExerciseId",
        planExerciseId = planExerciseId,
        date = "2026-06-18",
        weight = weight,
        reps = reps,
        rir = rir,
        loggedAt = "2026-06-18T10:00:00Z",
    )
}
