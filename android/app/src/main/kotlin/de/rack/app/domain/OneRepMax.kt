package de.rack.app.domain

import java.math.BigDecimal

/*
 * Selects the heaviest logged set from an exercise's history and turns it into an
 * Epley 1RM estimate (Phase 13, docs/specs/spec-plate-calc-1rm.md). Pure and
 * dependency-free: it reuses the [epleyOneRepMax] math (#81) and reads only the
 * set logs Phase 3 already exposes. The Composable renders the result string; this
 * function owns the "which set drives the number" decision so it stays unit-tested.
 *
 * "Heaviest logged set" is the entry with the greatest weight; ties are broken by
 * the higher rep count (the prior decision in the spec). A SetLog carries one
 * weight with per-set reps, so each (weight, repCount) pair is a candidate and
 * zero-rep entries are ignored.
 */

/** A single (weight, reps) candidate flattened out of the per-set reps of a [SetLog]. */
private data class WeightedSet(
    val weight: BigDecimal,
    val reps: Int,
)

/**
 * The Epley 1RM estimate from the heaviest set across [history], rounded to one
 * decimal, or null when no set carries a positive weight and rep (so the surface
 * shows the suppressed "—"). The heaviest set is the max weight, ties broken by the
 * higher reps; a single rep yields the lifted weight exactly via [epleyOneRepMax].
 */
fun heaviestSetOneRepMax(history: List<SetLog>): BigDecimal? {
    val heaviest =
        history
            .flatMap { log -> candidates(log) }
            .maxWithOrNull(compareBy(WeightedSet::weight, WeightedSet::reps))
            ?: return null
    return epleyOneRepMax(heaviest.weight, heaviest.reps)
}

/** Flatten a [log] into one candidate per positive rep at its positive weight. */
private fun candidates(log: SetLog): List<WeightedSet> {
    val weight = log.weight?.takeIf { it > 0.0 }?.let(BigDecimal::valueOf) ?: return emptyList()
    return log.reps.filter { it > 0 }.map { reps -> WeightedSet(weight, reps) }
}
