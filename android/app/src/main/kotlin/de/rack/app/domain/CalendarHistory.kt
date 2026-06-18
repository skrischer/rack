package de.rack.app.domain

import java.time.LocalDate
import java.time.YearMonth

/*
 * Pure, side-effect-free aggregation for the calendar/history screen (Phase 11,
 * docs/specs/spec-dashboards.md). The repository fetches the user's logged sets and
 * flattens each into a [LoggedSet]; these functions derive the calendar's marked
 * days, the navigable month range over the full logged history, and the per-day
 * detail (the logged exercises with weight + per-set reps + RIR) shown when a marked
 * day is selected.
 *
 * Decisions reused from the spec: a "session" is all sets sharing one logged date;
 * values are kept in the stored unit (kg) with no toggle (Phase 12 owns kg/lb).
 */

/** One exercise's logged sets on a single day, for the calendar day disclosure. */
data class LoggedExerciseEntry(
    val exerciseId: String,
    val exerciseName: String,
    val sets: List<LoggedSetEntry>,
)

/** One logged set within a day: its weight, per-set reps, and RIR. */
data class LoggedSetEntry(
    val weight: Double?,
    val reps: List<Int>,
    val rir: Int?,
)

/** The inclusive range of months that contain logged sets, for month navigation. */
data class HistoryRange(
    val earliest: YearMonth,
    val latest: YearMonth,
)

/** The distinct calendar dates on which the user logged at least one set. */
fun loggedDates(sets: List<LoggedSet>): Set<LocalDate> = sets.mapTo(LinkedHashSet()) { it.date }

/**
 * The inclusive span of months over which the user has logged sets, or `null` when
 * the history is empty. The calendar caps backward/forward navigation to this range
 * so it never pages into months that can hold no data.
 */
fun loggedHistoryRange(sets: List<LoggedSet>): HistoryRange? {
    if (sets.isEmpty()) return null
    val earliest = sets.minOf { it.date }
    val latest = sets.maxOf { it.date }
    return HistoryRange(earliest = YearMonth.from(earliest), latest = YearMonth.from(latest))
}

/**
 * The logged exercises on [date], grouped by exercise and preserving the logged
 * order, with each exercise's per-set weight/reps/RIR. Returns an empty list when no
 * set was logged on that date. Used to expand a selected calendar day.
 */
fun sessionDetail(
    sets: List<LoggedSet>,
    date: LocalDate,
): List<LoggedExerciseEntry> =
    sets.asSequence()
        .filter { it.date == date }
        .groupBy { it.exerciseId }
        .map { (exerciseId, exerciseSets) -> entryFor(exerciseId, exerciseSets) }

private fun entryFor(
    exerciseId: String,
    exerciseSets: List<LoggedSet>,
): LoggedExerciseEntry =
    LoggedExerciseEntry(
        exerciseId = exerciseId,
        exerciseName = exerciseSets.first().exerciseName,
        sets = exerciseSets.map { LoggedSetEntry(weight = it.weight, reps = it.reps, rir = it.rir) },
    )
