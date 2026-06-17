package de.rack.app.ui.plan

import de.rack.app.domain.PlanExercise
import de.rack.app.ui.theme.SupersetKind
import de.rack.app.ui.theme.supersetKind

/**
 * Groups [exercises] (already in `position` order) into [ExerciseGroup]s by
 * scanning consecutive equal `superset_label` values. Exercises without a label,
 * or whose label does not match its neighbours, are emitted as standalone
 * [SupersetKind.NONE] groups; a run of equal labels becomes a single grouped
 * entry classified by [supersetKind].
 */
fun groupExercises(exercises: List<PlanExercise>): List<ExerciseGroup> {
    val groups = mutableListOf<ExerciseGroup>()
    var index = 0
    while (index < exercises.size) {
        val current = exercises[index]
        val label = current.supersetLabel?.takeIf { it.isNotBlank() }
        if (label == null) {
            groups += ExerciseGroup(SupersetKind.NONE, listOf(current))
            index++
            continue
        }
        var end = index + 1
        while (end < exercises.size && exercises[end].supersetLabel?.takeIf { it.isNotBlank() } == label) {
            end++
        }
        val run = exercises.subList(index, end)
        val kind = supersetKind(run.size)
        groups += ExerciseGroup(kind, run.toList())
        index = end
    }
    return groups
}
