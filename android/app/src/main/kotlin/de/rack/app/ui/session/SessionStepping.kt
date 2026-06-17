package de.rack.app.ui.session

import de.rack.app.domain.PlanExercise
import de.rack.app.ui.logging.setCount
import de.rack.app.ui.plan.ExerciseGroup
import de.rack.app.ui.plan.groupExercises
import de.rack.app.ui.theme.SupersetKind

/**
 * Turns a plan day's position-ordered [exercises] into the ordered step sequence
 * the player walks through. Non-superset exercises step straight through all their
 * sets before the next exercise; a `superset_label` group rotates set-by-set
 * (set 1 of every member, then set 2 of every member, ...). Uneven set counts skip
 * an exhausted member in that and later rounds while the others keep rotating, so a
 * 4x5 next to a 3x8 finishes the 4th set of the first member alone.
 */
fun buildSessionSteps(exercises: List<PlanExercise>): List<SessionStep> =
    groupExercises(exercises).flatMap(::stepsForGroup)

private fun stepsForGroup(group: ExerciseGroup): List<SessionStep> {
    val members = group.exercises.map { it to setCount(it.target) }
    return when (group.kind) {
        SupersetKind.NONE -> straightSteps(group.kind, members)
        SupersetKind.SUPERSET, SupersetKind.CIRCUIT -> rotatedSteps(group.kind, members)
    }
}

/** All sets of each member in order: every set of the first exercise, then the next. */
private fun straightSteps(
    kind: SupersetKind,
    members: List<Pair<PlanExercise, Int>>,
): List<SessionStep> =
    members.flatMap { (exercise, total) ->
        (0 until total).map { setIndex -> step(exercise, kind, setIndex, total) }
    }

/**
 * Set-synchronized round-robin within a group: for each round (set index), emit a
 * step for every member that still has a set at that index, in group order. A
 * member with fewer sets is simply absent from later rounds, so the rotation
 * continues with the remaining members.
 */
private fun rotatedSteps(
    kind: SupersetKind,
    members: List<Pair<PlanExercise, Int>>,
): List<SessionStep> {
    val maxSets = members.maxOfOrNull { it.second } ?: 0
    return (0 until maxSets).flatMap { round ->
        members
            .filter { (_, total) -> round < total }
            .map { (exercise, total) -> step(exercise, kind, round, total) }
    }
}

private fun step(
    exercise: PlanExercise,
    kind: SupersetKind,
    setIndex: Int,
    totalSets: Int,
): SessionStep =
    SessionStep(
        planExerciseId = exercise.id,
        exerciseId = exercise.exerciseId,
        name = exercise.name,
        kind = kind,
        setIndex = setIndex,
        totalSets = totalSets,
    )
