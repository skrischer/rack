package de.rack.app.ui.session

import de.rack.app.domain.ExerciseType
import de.rack.app.domain.PlanExercise
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Covers the Phase-9 [classifyExerciseType] rule and [restPromptFor] group resolution
 * (docs/specs/spec-session-player.md "Prior decisions"): each catalog `category` /
 * `equipment` branch maps to compound or isolation, and a ticked set resolves its
 * superset/circuit context so the Phase-8 timer applies the group override. Phase 9
 * owns this classification; Phase 8 owns only the type -> duration map.
 */
class SessionClassificationTest {
    @Test
    fun `legs back and chest are compound`() {
        assertEquals(ExerciseType.COMPOUND, classifyExerciseType(exercise(category = "Legs")))
        assertEquals(ExerciseType.COMPOUND, classifyExerciseType(exercise(category = "Back")))
        assertEquals(ExerciseType.COMPOUND, classifyExerciseType(exercise(category = "Chest")))
    }

    @Test
    fun `arms shoulders abs and calves are always isolation`() {
        assertEquals(ExerciseType.ISOLATION, classifyExerciseType(exercise(category = "Arms")))
        assertEquals(ExerciseType.ISOLATION, classifyExerciseType(exercise(category = "Shoulders")))
        assertEquals(ExerciseType.ISOLATION, classifyExerciseType(exercise(category = "Abs")))
        assertEquals(ExerciseType.ISOLATION, classifyExerciseType(exercise(category = "Calves")))
    }

    @Test
    fun `unmapped or null category defaults to isolation`() {
        assertEquals(ExerciseType.ISOLATION, classifyExerciseType(exercise(category = "Cardio")))
        assertEquals(ExerciseType.ISOLATION, classifyExerciseType(exercise(category = null)))
    }

    @Test
    fun `cable accessory downgrades a non-legs compound to isolation`() {
        val chestCable = exercise(category = "Chest", equipment = listOf("Cable"))
        val backMachine = exercise(category = "Back", equipment = listOf("Machine (kettlebell-style isolation)"))

        assertEquals(ExerciseType.ISOLATION, classifyExerciseType(chestCable))
        assertEquals(ExerciseType.ISOLATION, classifyExerciseType(backMachine))
    }

    @Test
    fun `legs stays compound even with a cable or machine accessory`() {
        val legsCable = exercise(category = "Legs", equipment = listOf("Cable"))
        val legsMachine = exercise(category = "Legs", equipment = listOf("Machine (kettlebell-style isolation)"))

        assertEquals(ExerciseType.COMPOUND, classifyExerciseType(legsCable))
        assertEquals(ExerciseType.COMPOUND, classifyExerciseType(legsMachine))
    }

    @Test
    fun `barbell compound is not downgraded`() {
        val chestBarbell = exercise(category = "Chest", equipment = listOf("Barbell"))

        assertEquals(ExerciseType.COMPOUND, classifyExerciseType(chestBarbell))
    }

    @Test
    fun `rest prompt resolves type and standalone context for a non-grouped exercise`() {
        val exercises = listOf(exercise(id = "a", category = "Legs"))

        val prompt = restPromptFor(exercises, "a")

        assertEquals(ExerciseType.COMPOUND, prompt?.type)
        assertEquals(1, prompt?.context?.group?.size)
        assertEquals(0, prompt?.context?.index)
    }

    @Test
    fun `rest prompt resolves the superset group context for a grouped member`() {
        val exercises =
            listOf(
                exercise(id = "a", category = "Chest", label = "S1"),
                exercise(id = "b", category = "Back", label = "S1"),
            )

        val prompt = restPromptFor(exercises, "b")

        assertEquals(2, prompt?.context?.group?.size)
        assertEquals(1, prompt?.context?.index)
    }

    @Test
    fun `rest prompt is null for an absent exercise`() {
        assertNull(restPromptFor(listOf(exercise(id = "a")), "missing"))
    }

    private fun exercise(
        id: String = "a",
        category: String? = null,
        equipment: List<String> = emptyList(),
        label: String? = null,
    ) = PlanExercise(
        id = id,
        dayId = "d",
        exerciseId = id,
        name = id,
        category = category,
        position = 0,
        target = "3 x 8",
        rir = null,
        cue = null,
        supersetLabel = label,
        equipment = equipment,
    )
}
