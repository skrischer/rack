package de.rack.app.ui.session

import de.rack.app.domain.PlanExercise
import de.rack.app.domain.WeightUnit
import de.rack.app.domain.displayToKg
import de.rack.app.ui.logging.setCount

/**
 * The per-exercise line shown on the session summary: the exercise [name], how many
 * sets were ticked vs. its [targetSets] target, and the exercise's total [volume]
 * (its single per-exercise weight times the sum of the ticked sets' reps). Derived
 * from the ticked steps and the working entries; not persisted as such (the saved
 * `set_logs` row carries scalar weight, scalar rir, and `reps[]`).
 */
data class SessionSummaryLine(
    val planExerciseId: String,
    val name: String,
    val setsDone: Int,
    val targetSets: Int,
    val volume: Double,
)

/**
 * The finished session's summary: one [lines] entry per logged exercise (those with at
 * least one logged set) and the [totalVolume] across them. Built purely in the ViewModel;
 * the session duration is read from the Phase-8 session-duration timer and rendered
 * alongside this by the screen, not held here. The screen renders it and the
 * confirm/abandon actions act on it.
 */
data class SessionSummary(
    val lines: List<SessionSummaryLine>,
    val totalVolume: Double,
) {
    /** True when no exercise logged a set, so confirming would write nothing. */
    val isEmpty: Boolean get() = lines.isEmpty()
}

/**
 * Aggregates the finished session into a [SessionSummary]: for every exercise in the
 * day's [exercises] order it counts the ticked sets in [done], gathers their reps from
 * the per-exercise [entries], and computes volume = weight x sum(reps) in canonical kg
 * (the entered weight is in [unit] and converted with [displayToKg]). An exercise with
 * no logged set (no ticked set carrying any reps and no entered weight) is omitted,
 * mirroring the skip guard so the summary lists only what will be saved.
 */
fun buildSessionSummary(
    exercises: List<PlanExercise>,
    done: List<SessionStep>,
    entries: Map<String, ExerciseEntries>,
    unit: WeightUnit,
): SessionSummary {
    val lines =
        exercises.mapNotNull { exercise ->
            summaryLine(exercise, done, entries[exercise.id] ?: ExerciseEntries(), unit)
        }
    return SessionSummary(
        lines = lines,
        totalVolume = lines.sumOf(SessionSummaryLine::volume),
    )
}

private fun summaryLine(
    exercise: PlanExercise,
    done: List<SessionStep>,
    entries: ExerciseEntries,
    unit: WeightUnit,
): SessionSummaryLine? {
    val reps = loggedReps(exercise.id, done, entries)
    val weightKg = entries.weight.trim().toDoubleOrNull()?.let { displayToKg(it, unit) }
    if (reps.isEmpty() && weightKg == null) return null
    return SessionSummaryLine(
        planExerciseId = exercise.id,
        name = exercise.name,
        setsDone = reps.size,
        targetSets = setCount(exercise.target),
        volume = (weightKg ?: 0.0) * reps.sum(),
    )
}

/**
 * The reps logged for [planExerciseId], in ticked-set order: the entered reps of each
 * ticked set whose reps parse to a positive count. A ticked set left blank or with a
 * non-numeric/zero reps value is dropped (the per-exercise weight alone still keeps the
 * exercise in the summary so an isometric/weighted-only set is not silently lost).
 */
fun loggedReps(
    planExerciseId: String,
    done: List<SessionStep>,
    entries: ExerciseEntries,
): List<Int> =
    done
        .filter { it.planExerciseId == planExerciseId }
        .sortedBy { it.setIndex }
        .mapNotNull { step -> entries.reps[step.setIndex]?.trim()?.toIntOrNull()?.takeIf { it > 0 } }

/**
 * The live total volume (canonical kg) of the ticked sets so far, for the running session
 * stat strip: each ticked set contributes its exercise's entered weight (converted from
 * the display unit) times the set's entered reps. Sets with no weight or no positive reps
 * contribute zero. Pure; mirrors the summary's volume math without aggregating per line.
 */
fun SessionPlayerUiState.liveVolume(): Double =
    done.sumOf { step ->
        val exerciseEntries = entriesFor(step.planExerciseId)
        val weightKg = exerciseEntries.weight.trim().toDoubleOrNull()?.let { displayToKg(it, weightUnit) } ?: 0.0
        val reps = exerciseEntries.reps[step.setIndex]?.trim()?.toIntOrNull()?.takeIf { it > 0 } ?: 0
        weightKg * reps
    }
