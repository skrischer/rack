package de.rack.app.domain

import java.time.LocalDate
import java.time.temporal.IsoFields
import java.time.temporal.WeekFields

/**
 * The subset of [sets] logged in the same ISO week (Monday start, local timezone) as
 * [today]. The Home overview (Phase 11, docs/specs/spec-dashboards.md) uses this to
 * scope the weekly-volume breakdown to the current week before handing the slice to
 * [weeklyVolume]. Pure and order-preserving; correct across year/week boundaries
 * because the comparison is on the ISO week-based-year and week-of-week-based-year.
 */
fun currentIsoWeekSets(
    sets: List<LoggedSet>,
    today: LocalDate,
): List<LoggedSet> {
    val thisWeek = isoWeekOf(today)
    return sets.filter { isoWeekOf(it.date) == thisWeek }
}

/** The ISO-8601 (week-based-year, week-of-year) pair of [date], Monday start. */
private fun isoWeekOf(date: LocalDate): Pair<Int, Int> =
    date.get(IsoFields.WEEK_BASED_YEAR) to date.get(WeekFields.ISO.weekOfWeekBasedYear())
