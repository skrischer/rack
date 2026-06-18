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

/**
 * One exercise card in the set-table session view: the exercise's [name], read-only plan
 * [target] / [rir] / [cue], its set count, and its superset/circuit [kind]. [groupStart]
 * marks the first member of a `superset_label` run so the screen draws the group header
 * once above it. Static for the session (derived from the position-ordered exercises); the
 * live kg/RIR/reps and ticked state are held per `plan_exercise_id` in the ViewModel.
 */
data class SessionExerciseBlock(
    val planExerciseId: String,
    val name: String,
    val kind: SupersetKind,
    val setCount: Int,
    val target: String? = null,
    val rir: Int? = null,
    val cue: String? = null,
    val groupStart: Boolean = false,
)

/**
 * Turns the day's position-ordered [exercises] into one [SessionExerciseBlock] per
 * exercise, preserving order and tagging the first member of each superset/circuit run so
 * the set-table view renders the group header once. Reuses [groupExercises] so the grouping
 * matches the plan day view exactly.
 */
fun buildExerciseBlocks(exercises: List<PlanExercise>): List<SessionExerciseBlock> =
    groupExercises(exercises).flatMap { group ->
        group.exercises.mapIndexed { index, exercise ->
            SessionExerciseBlock(
                planExerciseId = exercise.id,
                name = exercise.name,
                kind = group.kind,
                setCount = setCount(exercise.target),
                target = exercise.target,
                rir = exercise.rir,
                cue = exercise.cue,
                groupStart = group.kind != SupersetKind.NONE && index == 0,
            )
        }
    }

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
        target = exercise.target,
        rir = exercise.rir,
        cue = exercise.cue,
    )
