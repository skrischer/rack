package de.rack.app.ui.session

import de.rack.app.domain.PlanExercise
import de.rack.app.domain.SetLog
import de.rack.app.domain.WeightUnit
import org.junit.Test
import kotlin.test.assertEquals

/**
 * Covers the pure session-player display logic the set-table screen renders (issue #58 /
 * #161, docs/specs/spec-session-player.md): [prefillEntries] seeds kg/RIR/reps from the
 * last log and the typed prescription, and [previousSets] builds the per-set "Vorher"
 * strings from the last logged session's matching set. kg and RIR are single per-exercise
 * values; reps and the previous-performance column are per-set.
 */
class SessionPrefillTest {
    @Test
    fun `prefill takes kg and rir from the last log and reps per set`() {
        val exercise = exercise("a", sets = 3, repMin = 8, repMax = 8, rirLow = 1)
        val last = log("a", weight = 60.0, reps = listOf(8, 7, 6), rir = 2)

        val entries = prefillEntries(listOf(exercise), mapOf("a" to last), WeightUnit.KG).getValue("a")

        assertEquals("60", entries.weight)
        assertEquals("2", entries.rir)
        assertEquals(mapOf(0 to "8", 1 to "7", 2 to "6"), entries.reps)
    }

    @Test
    fun `prefill converts the last log weight to the selected unit`() {
        val exercise = exercise("a", sets = 3, repMin = 8, repMax = 8, rirLow = 1)
        // 61.25 kg -> 135.0 lb at 0.5 lb steps.
        val last = log("a", weight = 61.25, reps = listOf(8), rir = 2)

        val entries = prefillEntries(listOf(exercise), mapOf("a" to last), WeightUnit.LB).getValue("a")

        assertEquals("135", entries.weight)
    }

    @Test
    fun `prefill falls back to the lower target rir and the rep range with no last log`() {
        val exercise = exercise("a", sets = 4, repMin = 5, repMax = 8, rirLow = 2)

        val entries = prefillEntries(listOf(exercise), emptyMap(), WeightUnit.KG).getValue("a")

        assertEquals("", entries.weight)
        assertEquals("2", entries.rir)
        assertEquals(mapOf(0 to "5–8", 1 to "5–8", 2 to "5–8", 3 to "5–8"), entries.reps)
    }

    @Test
    fun `previous shows the last session's matching set as weight times reps per set`() {
        val exercise = exercise("a", sets = 3, repMin = 8, repMax = 8, rirLow = 1)
        val last = log("a", weight = 82.5, reps = listOf(8, 7, 7), rir = 1)

        val previous = previousSets(listOf(exercise), mapOf("a" to last), WeightUnit.KG).getValue("a")

        assertEquals(listOf("82.5 × 8", "82.5 × 7", "82.5 × 7"), previous)
    }

    @Test
    fun `previous is blank per set with no last log or no matching rep`() {
        val exercise = exercise("a", sets = 3, repMin = 8, repMax = 8, rirLow = 1)
        // Last log has only two sets; the third has no matching previous rep.
        val last = log("a", weight = 80.0, reps = listOf(10, 8), rir = 1)

        val noLog = previousSets(listOf(exercise), emptyMap(), WeightUnit.KG).getValue("a")
        val partial = previousSets(listOf(exercise), mapOf("a" to last), WeightUnit.KG).getValue("a")

        assertEquals(listOf("", "", ""), noLog)
        assertEquals(listOf("80 × 10", "80 × 8", ""), partial)
    }

    private fun exercise(
        id: String,
        sets: Int?,
        repMin: Int? = null,
        repMax: Int? = null,
        rirLow: Int? = null,
    ) = PlanExercise(
        id = id,
        dayId = "d",
        exerciseId = id,
        name = id,
        category = null,
        position = 0,
        sets = sets,
        repMin = repMin,
        repMax = repMax,
        rirLow = rirLow,
        rirHigh = null,
        restSeconds = null,
        cue = null,
        supersetId = null,
        groupType = null,
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
