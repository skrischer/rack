package de.rack.app.data

import de.rack.app.domain.GroupType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Covers the wire-to-domain mapping of the typed plan prescription / grouping (#194,
 * docs/specs/spec-structured-prescription.md): [PlanExerciseDto.toDomain] carries the
 * typed `sets` / rep range / RIR range / rest and the explicit `superset_id` +
 * `group_type` onto [de.rack.app.domain.PlanExercise], folds the embedded catalog
 * join, and decodes the `plan_group_type` enum (unknown / absent -> null).
 */
class PlanExerciseMappingTest {
    @Test
    fun `typed prescription and a superset group map onto the domain model`() {
        val dto =
            PlanExerciseDto(
                id = "pe1",
                dayId = "d1",
                exerciseId = "ex1",
                position = 2,
                sets = 3,
                repMin = 6,
                repMax = 8,
                rirLow = 1,
                rirHigh = 2,
                restSeconds = 120,
                cue = "brace",
                supersetId = 1,
                groupType = "superset",
                exercises = ExerciseCatalogDto(name = "Bench Press", category = "Chest", equipment = listOf("Barbell")),
            )

        val exercise = dto.toDomain()

        assertEquals("pe1", exercise.id)
        assertEquals("d1", exercise.dayId)
        assertEquals("ex1", exercise.exerciseId)
        assertEquals(2, exercise.position)
        assertEquals(3, exercise.sets)
        assertEquals(6, exercise.repMin)
        assertEquals(8, exercise.repMax)
        assertEquals(1, exercise.rirLow)
        assertEquals(2, exercise.rirHigh)
        assertEquals(120, exercise.restSeconds)
        assertEquals("brace", exercise.cue)
        assertEquals(1, exercise.supersetId)
        assertEquals(GroupType.SUPERSET, exercise.groupType)
        assertEquals("Bench Press", exercise.name)
        assertEquals("Chest", exercise.category)
        assertEquals(listOf("Barbell"), exercise.equipment)
    }

    @Test
    fun `the circuit group type decodes to the circuit enum`() {
        val exercise =
            PlanExerciseDto(
                id = "p",
                dayId = "d",
                exerciseId = "e",
                position = 0,
                supersetId = 4,
                groupType = "circuit",
            ).toDomain()

        assertEquals(4, exercise.supersetId)
        assertEquals(GroupType.CIRCUIT, exercise.groupType)
    }

    @Test
    fun `an absent prescription and grouping map to nulls and an empty catalog`() {
        val exercise = PlanExerciseDto(id = "p", dayId = "d", exerciseId = "e", position = 0).toDomain()

        assertNull(exercise.sets)
        assertNull(exercise.repMin)
        assertNull(exercise.repMax)
        assertNull(exercise.rirLow)
        assertNull(exercise.rirHigh)
        assertNull(exercise.restSeconds)
        assertNull(exercise.supersetId)
        assertNull(exercise.groupType)
        assertEquals("", exercise.name)
        assertNull(exercise.category)
        assertEquals(emptyList(), exercise.equipment)
    }

    @Test
    fun `an unknown group type value decodes to null`() {
        val exercise =
            PlanExerciseDto(
                id = "p",
                dayId = "d",
                exerciseId = "e",
                position = 0,
                groupType = "giant-set",
            ).toDomain()

        assertNull(exercise.groupType)
    }
}
