package de.rack.app.ui.session

import de.rack.app.domain.ExerciseType
import de.rack.app.domain.PlanExercise
import de.rack.app.ui.plan.LoggedExerciseContext
import de.rack.app.ui.plan.groupExercises

/*
 * The Phase-9 compound/isolation classification rule (docs/specs/spec-session-player.md,
 * "Prior decisions"). The wger catalog has no compound flag, so this maps the loaded
 * catalog `category` (muscle group) plus `equipment` to a resolved [ExerciseType]; the
 * session player passes that type to the Phase-8 rest timer, which owns the type ->
 * duration map. Phase 9 owns the classification, Phase 8 owns only the durations, so
 * neither side duplicates the other. A pure function (not in a Composable) called from
 * the SessionPlayerViewModel and unit tested per branch.
 */

/**
 * The wger `category` (muscle-group) names trained with multi-joint movements that
 * rest long. Legs/Back/Chest are the big compound groups; Arms, Shoulders, Abs, and
 * Calves (and any unmapped category) stay isolation, the shorter-rest fallback.
 */
private val COMPOUND_PLAYER_CATEGORIES: Set<String> = setOf("legs", "back", "chest")

/**
 * Single-joint accessory implements that downgrade a non-Legs compound category to
 * isolation: cable and isolation-machine work rests short even on a big muscle group.
 */
private val ISOLATION_EQUIPMENT: Set<String> =
    setOf("cable", "machine (kettlebell-style isolation)")

private const val LEGS_CATEGORY = "legs"

/**
 * Resolves the compound/isolation rest-default type for [exercise] from its catalog
 * `category` and `equipment`. Defaults to [ExerciseType.ISOLATION]; returns
 * [ExerciseType.COMPOUND] when the category is Legs, Back, or Chest, except it
 * downgrades back to isolation when the equipment is a single-joint accessory (Cable
 * or the isolation machine) and the category is not Legs. The superset/circuit group
 * context, when present, overrides this to the group default in the Phase-8 timer.
 */
fun classifyExerciseType(exercise: PlanExercise): ExerciseType {
    val category = exercise.category?.trim()?.lowercase()
    val isCompound =
        category in COMPOUND_PLAYER_CATEGORIES &&
            !(category != LEGS_CATEGORY && hasIsolationEquipment(exercise.equipment))
    return if (isCompound) ExerciseType.COMPOUND else ExerciseType.ISOLATION
}

private fun hasIsolationEquipment(equipment: List<String>): Boolean =
    equipment.any { it.trim().lowercase() in ISOLATION_EQUIPMENT }

/**
 * The auto-prompt for the Phase-8 rest timer raised when a session set is ticked: the
 * focused exercise's resolved [type] and its superset/circuit [context]. The player's
 * Route forwards this to the Phase-8 timer (which owns the type -> duration map and the
 * countdown); the player itself reimplements no timer logic.
 */
data class RestPrompt(
    val type: ExerciseType,
    val context: LoggedExerciseContext,
)

/**
 * Builds the [RestPrompt] for the just-ticked [planExerciseId] from the day's
 * [exercises]: it classifies the exercise via [classifyExerciseType] and resolves its
 * superset/circuit group context (the consecutive `superset_id` run plus the
 * exercise's index in it) the same way the Phase-3 log flow does, so the timer applies
 * the group override. Null when the id is not in the day.
 */
fun restPromptFor(
    exercises: List<PlanExercise>,
    planExerciseId: String,
): RestPrompt? {
    groupExercises(exercises).forEach { group ->
        val index = group.exercises.indexOfFirst { it.id == planExerciseId }
        if (index >= 0) {
            val exercise = group.exercises[index]
            return RestPrompt(classifyExerciseType(exercise), LoggedExerciseContext(group.exercises, index))
        }
    }
    return null
}
