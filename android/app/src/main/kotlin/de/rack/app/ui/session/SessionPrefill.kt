package de.rack.app.ui.session

import de.rack.app.domain.PlanExercise
import de.rack.app.domain.SetLog
import de.rack.app.domain.WeightUnit
import de.rack.app.domain.formatWeight
import de.rack.app.ui.logging.setCount

/**
 * Builds the pre-filled working entries for every exercise in the session from its
 * target and its last-logged set (docs/specs/spec-session-player.md): the weight in the
 * selected [unit] from the last log's canonical kg, RIR from the last log's RIR or the
 * plan's target RIR, and each set's reps from the last log's matching set or the target
 * rep range. The user edits any field before ticking; a set left with no reps and no
 * exercise weight is skipped (not recorded), so these pre-fills only seed the common case.
 */
fun prefillEntries(
    exercises: List<PlanExercise>,
    lastLogs: Map<String, SetLog>,
    unit: WeightUnit,
): Map<String, ExerciseEntries> =
    exercises.associate { exercise ->
        exercise.id to entriesFor(exercise, lastLogs[exercise.id], unit)
    }

private fun entriesFor(
    exercise: PlanExercise,
    last: SetLog?,
    unit: WeightUnit,
): ExerciseEntries =
    ExerciseEntries(
        weight = last?.weight?.let { formatWeight(it, unit) }.orEmpty(),
        rir = (last?.rir ?: exercise.rir)?.toString().orEmpty(),
        reps = prefillReps(exercise, last),
    )

/**
 * The pre-filled reps text per set index: the last log's reps when present, else the
 * target rep range (e.g. "5-8" from a "4 × 5-8" target). The map covers exactly the
 * exercise's target set count so the player renders one reps field per set.
 */
private fun prefillReps(
    exercise: PlanExercise,
    last: SetLog?,
): Map<Int, String> {
    val sets = setCount(exercise.target)
    val targetReps = targetRepRange(exercise.target)
    return (0 until sets).associateWith { index ->
        last?.reps?.getOrNull(index)?.takeIf { it > 0 }?.toString() ?: targetReps
    }
}

/** The reps portion of a "sets × reps" target (e.g. "4 × 5-8" -> "5-8"); empty when absent. */
private fun targetRepRange(target: String?): String =
    target?.substringAfter('×', "")?.ifBlank { target.substringAfter('x', "") }?.trim().orEmpty()

/** "weight UNIT · r1/r2/r3" reference line for the focused exercise's "last time" summary. */
fun referenceLine(
    log: SetLog,
    unit: WeightUnit,
): String {
    val weight = log.weight?.let { formatWeight(it, unit) } ?: "–"
    val reps = log.reps.filter { it > 0 }.joinToString(separator = "/").ifEmpty { "–" }
    return "$weight ${unit.wire} · $reps"
}
