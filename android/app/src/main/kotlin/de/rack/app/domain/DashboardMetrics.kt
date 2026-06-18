package de.rack.app.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.IsoFields
import java.time.temporal.WeekFields

/*
 * Pure, side-effect-free aggregation over the user's logged sets, plus the domain
 * model the dashboards consume (docs/specs/spec-dashboards.md, Phase 11). The
 * repository fetches `set_logs` joined to `plan_exercises -> exercises` and
 * `plan_days`, flattens each row into a [LoggedSet], and these functions derive
 * the read-only overview metrics: weekly volume per muscle/tag, working-set
 * counts, the ISO-week streak, the session list, and per-exercise progress.
 *
 * Phase decisions encoded here (spec "Prior decisions"):
 * - "volume" = sum(weight x reps) over a set's reps[]; weight-null sets contribute
 *   zero volume but still count as working sets.
 * - muscle attribution = full credit per listed muscle (no fractional split);
 *   a set with no muscles falls into [OTHER_MUSCLE]; a null plan-day tag falls
 *   into [UNTAGGED_TAG].
 * - a "session" = all logged sets sharing one logged calendar date.
 * - streak = consecutive ISO weeks (Monday start, local timezone) with >=1 session,
 *   counted back from the current week.
 */

/** Bucket label for sets whose catalog exercise lists no muscles. */
const val OTHER_MUSCLE = "Other"

/** Bucket label for sets whose plan day carries no tag. */
const val UNTAGGED_TAG = "untagged"

/**
 * One logged set flattened from a `set_logs` row joined through `plan_exercises`
 * to its catalog `exercises` (muscles, category) and its `plan_days` (tag, title).
 * [date] is the logged calendar date (the session key); a row with no date is
 * dropped before aggregation since it cannot be attributed to a week or session.
 */
data class LoggedSet(
    val id: String,
    val date: LocalDate,
    val weight: Double?,
    val reps: List<Int>,
    val rir: Int?,
    val exerciseId: String,
    val exerciseName: String,
    val muscles: List<String>,
    val category: String?,
    val planDayTag: String?,
    val planDayTitle: String?,
) {
    /** Σ(weight × reps) for this set; zero when weight is null (bodyweight/time-only). */
    val volume: Double
        get() = weight?.let { w -> reps.sumOf { rep -> w * rep } } ?: 0.0
}

/** Total working sets and total weighted volume for a slice of logged sets. */
data class VolumeTotals(
    val workingSets: Int,
    val volume: Double,
)

/** Weekly aggregation: per-muscle and per-tag totals plus the overall totals. */
data class WeeklyVolume(
    val perMuscle: Map<String, VolumeTotals>,
    val perTag: Map<String, VolumeTotals>,
    val totals: VolumeTotals,
)

/** Current and longest run of consecutive ISO weeks containing at least one session. */
data class StreakStats(
    val current: Int,
    val longest: Int,
)

/** One logged session (all sets on a date), newest-first ordered by the caller. */
data class SessionSummary(
    val date: LocalDate,
    val title: String?,
    val tag: String?,
    val workingSets: Int,
    val volume: Double,
)

/** One per-session point on the per-exercise progress chart/table. */
data class ExerciseProgressPoint(
    val date: LocalDate,
    val topSetWeight: Double?,
    val volume: Double,
)

/**
 * Per-muscle and per-tag volume/working-set totals over [sets] (typically already
 * filtered to one ISO week by the caller). A set is credited fully to each of its
 * listed muscles; a set with no muscles lands in [OTHER_MUSCLE] and a set whose
 * plan day has no tag lands in [UNTAGGED_TAG].
 */
fun weeklyVolume(sets: List<LoggedSet>): WeeklyVolume {
    val perMuscle = LinkedHashMap<String, MutableTotals>()
    val perTag = LinkedHashMap<String, MutableTotals>()
    val totals = MutableTotals()
    for (set in sets) {
        val attributedMuscles = set.muscles.ifEmpty { listOf(OTHER_MUSCLE) }
        attributedMuscles.forEach { muscle -> perMuscle.getOrPut(muscle, ::MutableTotals).add(set) }
        perTag.getOrPut(set.planDayTag ?: UNTAGGED_TAG, ::MutableTotals).add(set)
        totals.add(set)
    }
    return WeeklyVolume(perMuscle.freeze(), perTag.freeze(), totals.toTotals())
}

/**
 * The current and longest streak of consecutive ISO weeks (Monday start, local
 * timezone) that contain at least one session, where the current streak is counted
 * back from [today]'s week. An empty history yields a zero streak. The week of a
 * set is its ISO week-of-week-based-year, so the count is correct across year and
 * week boundaries.
 */
fun streakStats(
    sets: List<LoggedSet>,
    today: LocalDate,
): StreakStats {
    val weeks = sets.map { isoWeekKey(it.date) }.toSortedSet()
    if (weeks.isEmpty()) return StreakStats(current = 0, longest = 0)
    val longest = longestRun(weeks)
    val current = currentRun(weeks, isoWeekKey(today))
    return StreakStats(current = current, longest = longest)
}

/**
 * The logged sessions (sets grouped by [LoggedSet.date]) as summaries, newest
 * first. Each session's [SessionSummary.title]/[SessionSummary.tag] come from the
 * day represented by its first logged set; [limit], when non-null, caps the list
 * (Home shows the last 10, the calendar shows all).
 */
fun sessionSummaries(
    sets: List<LoggedSet>,
    limit: Int? = null,
): List<SessionSummary> {
    val byDate = sets.groupBy { it.date }
    val summaries =
        byDate.entries
            .sortedByDescending { it.key }
            .map { (date, daySets) -> summariseSession(date, daySets) }
    return limit?.let(summaries::take) ?: summaries
}

/**
 * The per-session progress points for [exerciseId] over time, oldest first: for
 * each logged date, the top (heaviest) set weight and the total weighted volume.
 * Dates on which the exercise was logged only with bodyweight/time-only sets carry
 * a null [ExerciseProgressPoint.topSetWeight] and zero volume.
 */
fun exerciseProgress(
    sets: List<LoggedSet>,
    exerciseId: String,
): List<ExerciseProgressPoint> =
    sets.asSequence()
        .filter { it.exerciseId == exerciseId }
        .groupBy { it.date }
        .entries
        .sortedBy { it.key }
        .map { (date, daySets) -> progressPoint(date, daySets) }

private fun summariseSession(
    date: LocalDate,
    daySets: List<LoggedSet>,
): SessionSummary {
    val totals = MutableTotals().also { acc -> daySets.forEach(acc::add) }
    val first = daySets.first()
    return SessionSummary(
        date = date,
        title = first.planDayTitle,
        tag = first.planDayTag,
        workingSets = totals.workingSets,
        volume = totals.volume,
    )
}

private fun progressPoint(
    date: LocalDate,
    daySets: List<LoggedSet>,
): ExerciseProgressPoint =
    ExerciseProgressPoint(
        date = date,
        topSetWeight = daySets.mapNotNull { it.weight }.maxOrNull(),
        volume = daySets.sumOf { it.volume },
    )

/** The ISO-8601 week of [date] as (week-based-year, week-of-year), Monday start. */
private fun isoWeekKey(date: LocalDate): IsoWeek =
    IsoWeek(
        year = date.get(IsoFields.WEEK_BASED_YEAR),
        week = date.get(WeekFields.ISO.weekOfWeekBasedYear()),
    )

/** The longest run of consecutive weeks present in the ascending [weeks] set. */
private fun longestRun(weeks: Set<IsoWeek>): Int {
    var longest = 0
    var run = 0
    var previous: IsoWeek? = null
    for (week in weeks) {
        run = if (previous != null && week == previous.next()) run + 1 else 1
        if (run > longest) longest = run
        previous = week
    }
    return longest
}

/** The run of consecutive weeks ending at [thisWeek], walking backwards. */
private fun currentRun(
    weeks: Set<IsoWeek>,
    thisWeek: IsoWeek,
): Int {
    var run = 0
    var cursor = thisWeek
    while (cursor in weeks) {
        run += 1
        cursor = cursor.previous()
    }
    return run
}

/** An ISO week-based-year/week pair with adjacency helpers via a Monday anchor. */
private data class IsoWeek(
    val year: Int,
    val week: Int,
) : Comparable<IsoWeek> {
    private val monday: LocalDate
        get() =
            LocalDate.now()
                .with(WeekFields.ISO.weekBasedYear(), year.toLong())
                .with(WeekFields.ISO.weekOfWeekBasedYear(), week.toLong())
                .with(DayOfWeek.MONDAY)

    fun next(): IsoWeek = fromMonday(monday.plusWeeks(1))

    fun previous(): IsoWeek = fromMonday(monday.minusWeeks(1))

    override fun compareTo(other: IsoWeek): Int = compareValuesBy(this, other, IsoWeek::year, IsoWeek::week)

    private companion object {
        fun fromMonday(monday: LocalDate): IsoWeek =
            IsoWeek(
                year = monday.get(IsoFields.WEEK_BASED_YEAR),
                week = monday.get(WeekFields.ISO.weekOfWeekBasedYear()),
            )
    }
}

/** Mutable accumulator so a set is folded once into multiple buckets in one pass. */
private class MutableTotals {
    var workingSets: Int = 0
    var volume: Double = 0.0

    fun add(set: LoggedSet) {
        workingSets += 1
        volume += set.volume
    }

    fun toTotals(): VolumeTotals = VolumeTotals(workingSets = workingSets, volume = volume)
}

private fun Map<String, MutableTotals>.freeze(): Map<String, VolumeTotals> =
    mapValues { (_, totals) -> totals.toTotals() }
