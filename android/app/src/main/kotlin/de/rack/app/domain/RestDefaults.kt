package de.rack.app.domain

import de.rack.app.ui.theme.SupersetKind
import de.rack.app.ui.theme.supersetKind

/*
 * Built-in rest-default constants and the pure resolvers that map an exercise to
 * its rest duration and superset/circuit role from the existing schema fields
 * (see docs/specs/spec-timers.md). This is Phase-8 client-only logic: no
 * persistence, no settings, no Supabase access. Per-user override of these
 * defaults is deferred to Phase 12 (docs/specs/spec-settings.md).
 *
 * The default durations are the midpoints of the Recomp ranges (compound 2-3 min,
 * isolation 60-90 s, superset 60-90 s, circuit 30-45 s). Exercise type resolves
 * from the existing schema: the catalog `category` decides compound vs isolation
 * via [COMPOUND_CATEGORIES] (isolation is the safe fallback for any unmapped
 * category), while the `superset_label` group size decides superset (2 members)
 * vs circuit (3+ members). Group size is the count of `plan_exercises` in the
 * same `plan_day` sharing the same `superset_label` value.
 */

/** Default rest after a compound (multi-joint) exercise: 150 s (2.5 min midpoint). */
const val REST_COMPOUND_SECONDS = 150

/** Default rest after an isolation exercise: 75 s (60-90 s midpoint). */
const val REST_ISOLATION_SECONDS = 75

/** Default rest between superset (2-member) stations: 75 s (60-90 s midpoint). */
const val REST_SUPERSET_SECONDS = 75

/** Default rest between circuit (3+-member) stations: 38 s (30-45 s midpoint). */
const val REST_CIRCUIT_SECONDS = 38

/**
 * The wger `category` names treated as compound (multi-joint) movements. wger's
 * `category` is a coarse muscle-group string (Abs, Arms, Back, Calves, Cardio,
 * Chest, Legs, Shoulders), not a compound flag, so this name map covers the
 * common compound groups; every other (or unmapped) category falls back to
 * isolation, the conservative shorter rest that avoids over-resting.
 */
val COMPOUND_CATEGORIES: Set<String> = setOf("back", "chest", "legs", "shoulders")

/**
 * The resolved movement type that selects the standalone rest default: a
 * multi-joint [COMPOUND] (longer rest) or a single-joint [ISOLATION] (shorter
 * rest). It is the contract Phase 8 exposes to callers: the caller classifies the
 * exercise into one of these (Phase 9 owns the catalog `category`/`equipment` rule)
 * and Phase 8 owns only the type -> duration map below via [restSecondsFor].
 */
enum class ExerciseType {
    COMPOUND,
    ISOLATION,
}

/**
 * The single Phase-8 type -> duration map: resolves the initial rest seconds from a
 * caller-resolved exercise [type] and its `superset_label` [groupSize] (the count
 * of plan-exercises in the same plan-day sharing the same label). A group of 2 rests
 * as a superset and 3+ as a circuit, overriding the per-exercise [type]; a standalone
 * exercise rests by its [type] (compound 2-3 min, isolation 60-90 s). This is the only
 * place the type -> duration mapping lives, so no caller duplicates it.
 */
fun restSecondsFor(
    type: ExerciseType,
    groupSize: Int,
): Int =
    when (supersetKind(groupSize)) {
        SupersetKind.SUPERSET -> REST_SUPERSET_SECONDS
        SupersetKind.CIRCUIT -> REST_CIRCUIT_SECONDS
        SupersetKind.NONE -> if (type == ExerciseType.COMPOUND) REST_COMPOUND_SECONDS else REST_ISOLATION_SECONDS
    }

/**
 * Resolves the initial rest duration (seconds) for an exercise from its catalog
 * [category] and its `superset_label` [groupSize] (the count of plan-exercises in
 * the same plan-day sharing the same label). The coarse category -> type step uses
 * [isCompoundCategory]; the type -> duration step delegates to [restSecondsFor] so the
 * duration map stays single-sourced. The Phase-3 log flow keeps this category-based
 * entry; the Phase-9 session player classifies via its own richer rule and calls
 * [restSecondsFor] with the resolved type instead.
 */
fun resolveRestSeconds(
    category: String?,
    groupSize: Int,
): Int =
    restSecondsFor(
        type = if (isCompoundCategory(category)) ExerciseType.COMPOUND else ExerciseType.ISOLATION,
        groupSize = groupSize,
    )

/**
 * True when [category] maps to a compound (multi-joint) group in
 * [COMPOUND_CATEGORIES]; null/blank/unmapped categories are isolation (the safe
 * shorter-rest fallback).
 */
fun isCompoundCategory(category: String?): Boolean = category?.trim()?.lowercase() in COMPOUND_CATEGORIES

/**
 * The role of a logged exercise's group and the next station to perform, used to
 * render the superset/circuit rotation cue on a logged set. [role] is single,
 * superset, or circuit; [next] names the exercise to perform before the next rest
 * (the next member, wrapping back to the first), or null for a single exercise.
 */
data class GroupRotation(
    val role: SupersetKind,
    val next: PlanExercise?,
)

/**
 * Returns the [GroupRotation] for the exercise at [currentIndex] within its
 * superset/circuit [group] (the position-ordered run sharing one `superset_label`).
 * For a single exercise (or an out-of-range index) the role is
 * [SupersetKind.NONE] with no next station; for a superset/circuit the next is the
 * following member, wrapping to the first after the last.
 */
fun resolveGroupRotation(
    group: List<PlanExercise>,
    currentIndex: Int,
): GroupRotation {
    val role = supersetKind(group.size)
    if (role == SupersetKind.NONE || currentIndex !in group.indices) {
        return GroupRotation(SupersetKind.NONE, null)
    }
    val next = group[(currentIndex + 1) % group.size]
    return GroupRotation(role, next)
}
