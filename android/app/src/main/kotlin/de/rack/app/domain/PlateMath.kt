package de.rack.app.domain

import java.math.BigDecimal
import java.math.RoundingMode

/*
 * Pure, dependency-free plate-math and Epley 1RM functions (Phase 13, see
 * docs/specs/spec-plate-calc-1rm.md). Client-only: no persistence, no settings,
 * no Supabase access. Persistence of the bar/inventory preferences (#82) and the
 * Compose surface (#83/#84) build on these functions; this step owns only the
 * deterministic, unit-tested math.
 *
 * All weights are in kg. To avoid float drift on 2.5/1.25 kg plates, the split
 * works in BigDecimal throughout; callers render the rounded result.
 */

/** A pair of plates (one per side) of a given [weight], used [count] times. */
data class PlateSlot(
    val weight: BigDecimal,
    val count: Int,
)

/**
 * The result of splitting a target weight onto a bar: the per-side [plates]
 * stacked largest-first, the [loadableWeight] actually reachable (bar plus
 * loaded plates), and the [shortfall] (target minus loadable, never negative).
 * [belowBar] is true when the target is below the bar weight, in which case no
 * plates are loaded and the loadable weight is the target itself.
 */
data class PlateResult(
    val plates: List<PlateSlot>,
    val loadableWeight: BigDecimal,
    val shortfall: BigDecimal,
    val belowBar: Boolean,
)

/** Default Olympic bar weight in kg. */
val DEFAULT_BAR_WEIGHT: BigDecimal = BigDecimal("20")

/** Default standard kg plate inventory, each available as the given pair count. */
val DEFAULT_INVENTORY: List<PlateSlot> =
    listOf("25", "20", "15", "10", "5", "2.5", "1.25").map { PlateSlot(BigDecimal(it), Int.MAX_VALUE) }

private val TWO = BigDecimal(2)

/** Epley's denominator: `1RM = w * (1 + reps/30)`. */
private val EPLEY_DIVISOR = BigDecimal(30)

/** Working scale for the Epley rep factor before the final one-decimal rounding. */
private const val EPLEY_FACTOR_SCALE = 10

/**
 * Splits [targetWeight] onto a [barWeight] using [inventory] (pair-counted plate
 * stock), greedily loading the largest plate that fits per side first. Plates are
 * consumed in pairs and each plate's pair count caps its use. Returns the per-side
 * stack, the achieved loadable weight (<= target), and any labeled shortfall;
 * a target below the bar yields the below-bar state with no plates.
 */
fun splitPlates(
    targetWeight: BigDecimal,
    barWeight: BigDecimal = DEFAULT_BAR_WEIGHT,
    inventory: List<PlateSlot> = DEFAULT_INVENTORY,
): PlateResult {
    if (targetWeight < barWeight) {
        return PlateResult(emptyList(), targetWeight, BigDecimal.ZERO, belowBar = true)
    }
    var perSideRemaining = targetWeight.subtract(barWeight).divide(TWO)
    val plates = mutableListOf<PlateSlot>()
    inventory.sortedByDescending { it.weight }.forEach { stock ->
        if (stock.weight.signum() > 0 && stock.count > 0) {
            val used = perSideRemaining.divideToIntegralValue(stock.weight).toInt().coerceAtMost(stock.count)
            if (used > 0) {
                plates += PlateSlot(stock.weight, used)
                perSideRemaining = perSideRemaining.subtract(stock.weight.multiply(BigDecimal(used)))
            }
        }
    }
    val loadable = targetWeight.subtract(perSideRemaining.multiply(TWO))
    return PlateResult(plates, loadable, targetWeight.subtract(loadable), belowBar = false)
}

/**
 * Epley one-rep-max estimate `1RM = w * (1 + reps/30)`, rounded to one decimal.
 * Returns null (the suppressed value) when [weight] is not positive or [reps] is
 * not positive; a single rep already is the one-rep max, so reps = 1 returns
 * [weight] itself rather than applying the multi-rep formula.
 */
fun epleyOneRepMax(
    weight: BigDecimal?,
    reps: Int,
): BigDecimal? {
    return when {
        weight == null || weight.signum() <= 0 || reps <= 0 -> null
        reps == 1 -> weight.setScale(1, RoundingMode.HALF_UP)
        else -> {
            val factor =
                BigDecimal.ONE.add(BigDecimal(reps).divide(EPLEY_DIVISOR, EPLEY_FACTOR_SCALE, RoundingMode.HALF_UP))
            weight.multiply(factor).setScale(1, RoundingMode.HALF_UP)
        }
    }
}
