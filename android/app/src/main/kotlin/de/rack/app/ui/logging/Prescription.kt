package de.rack.app.ui.logging

import de.rack.app.domain.PlanExercise

/*
 * Pure formatters that render the typed plan prescription (Phase 15,
 * docs/specs/spec-structured-prescription.md) for the plan and session views: a
 * rep range, an RIR range, a rest, and the combined per-exercise label. Minimal
 * correctness rendering — the visual restyle is the Recomp UX Overhaul living-spec.
 */

/** A numeric range as `min–max` (en-dash), a single bound when only one is set, or "" when neither. */
internal fun rangeText(
    min: Int?,
    max: Int?,
): String =
    when {
        min != null && max != null && min != max -> "$min–$max"
        min != null -> min.toString()
        max != null -> max.toString()
        else -> ""
    }

/** The rep range, e.g. "6–8" or "8"; "" when no rep range is set. */
internal fun repRangeText(
    repMin: Int?,
    repMax: Int?,
): String = rangeText(repMin, repMax)

/** The RIR range prefixed with "RIR", e.g. "RIR 1–2"; "" when no RIR range is set. */
internal fun rirRangeText(
    rirLow: Int?,
    rirHigh: Int?,
): String = rangeText(rirLow, rirHigh).let { if (it.isEmpty()) "" else "RIR $it" }

/** The rest as "120s"; "" when no positive rest is set. */
internal fun restText(restSeconds: Int?): String = restSeconds?.takeIf { it > 0 }?.let { "${it}s" }.orEmpty()

/** The sets × reps fragment, e.g. "3 × 6–8", "3", or "6–8"; "" when neither is set. */
internal fun setsRepsText(
    sets: Int?,
    repMin: Int?,
    repMax: Int?,
): String = listOfNotNull(sets?.toString(), repRangeText(repMin, repMax).ifEmpty { null }).joinToString(" × ")

/**
 * The combined one-line prescription label for [exercise], e.g.
 * "3 × 6–8 · RIR 1–2 · 120s". Null when the exercise carries no typed prescription.
 */
internal fun prescriptionLabel(exercise: PlanExercise): String? =
    listOf(
        setsRepsText(exercise.sets, exercise.repMin, exercise.repMax),
        rirRangeText(exercise.rirLow, exercise.rirHigh),
        restText(exercise.restSeconds),
    ).filter { it.isNotEmpty() }
        .joinToString(" · ")
        .ifEmpty { null }
