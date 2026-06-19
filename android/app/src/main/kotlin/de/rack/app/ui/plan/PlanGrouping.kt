package de.rack.app.ui.plan

import de.rack.app.domain.PlanExercise
import de.rack.app.ui.theme.SupersetKind
import de.rack.app.ui.theme.supersetKind

/**
 * Groups [exercises] (already in `position` order) into [ExerciseGroup]s by
 * scanning consecutive equal `superset_id` values. Exercises without a group id,
 * or whose id does not match its neighbours, are emitted as standalone
 * [SupersetKind.NONE] groups; a run of equal ids becomes a single grouped entry
 * classified by [supersetKind] from its members' `group_type`.
 */
fun groupExercises(exercises: List<PlanExercise>): List<ExerciseGroup> {
    val groups = mutableListOf<ExerciseGroup>()
    var index = 0
    while (index < exercises.size) {
        val current = exercises[index]
        val groupId = current.supersetId
        if (groupId == null) {
            groups += ExerciseGroup(SupersetKind.NONE, listOf(current))
            index++
            continue
        }
        var end = index + 1
        while (end < exercises.size && exercises[end].supersetId == groupId) {
            end++
        }
        val run = exercises.subList(index, end).toList()
        val kind = supersetKind(current.groupType, run.size)
        groups += ExerciseGroup(kind, run)
        index = end
    }
    return groups
}
