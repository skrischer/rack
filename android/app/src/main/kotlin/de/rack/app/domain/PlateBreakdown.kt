package de.rack.app.domain

import java.math.BigDecimal

/*
 * Pure bridge between the persisted plate-calculator preferences (#82, Double-backed
 * [PlateCalcPreferences]) and the BigDecimal plate-math (#81, [splitPlates]), plus the
 * derived UI breakdown the screen renders (#83, docs/specs/spec-plate-calc-1rm.md).
 *
 * Kept out of the ViewModel and Composables so the mapping, the greedy split call, and
 * the rendered states (below-bar, exact, short, empty) stay deterministic and unit-tested.
 * Phase 13 is kg-only: weights here are kilograms and never consult the Phase-12 unit.
 */

/** The display breakdown the calculator renders for a target weight and the prefs. */
sealed interface PlateBreakdown {
    /** No valid target entered yet — nothing to load. */
    data object Empty : PlateBreakdown

    /** Target is below the bar weight; no plates load. [barWeight] is shown for context. */
    data class BelowBar(
        val barWeight: BigDecimal,
    ) : PlateBreakdown

    /**
     * A loadable target: the per-side [perSide] stack (largest-first), the achieved
     * [total] bar weight, the configured [barWeight], and any [shortfall] (> 0 when the
     * inventory cannot hit the target exactly).
     */
    data class Loadable(
        val perSide: List<PlateSlot>,
        val barWeight: BigDecimal,
        val total: BigDecimal,
        val shortfall: BigDecimal,
    ) : PlateBreakdown {
        /** True when the target was hit exactly (no remaining shortfall). */
        val isExact: Boolean get() = shortfall.signum() == 0
    }
}

/** The configured bar weight as a [BigDecimal] the math consumes. */
fun PlateCalcPreferences.barWeightDecimal(): BigDecimal = kgDecimal(barWeightKg)

/** The inventory mapped to the math's pair-counted [PlateSlot] stock. */
fun PlateCalcPreferences.inventorySlots(): List<PlateSlot> =
    inventory.map { PlateSlot(weight = kgDecimal(it.plateKg), count = it.pairCount) }

/**
 * Convert a kilogram [value] to a normalized [BigDecimal] (no trailing zeros, no negative
 * scale), e.g. 25.0 -> "25", 2.5 -> "2.5". This keeps the math's plate weights at the same
 * canonical scale as [PlateMath]'s defaults so equality and rendering stay drift-free.
 */
private fun kgDecimal(value: Double): BigDecimal {
    val normalized = BigDecimal.valueOf(value).stripTrailingZeros()
    return if (normalized.scale() < 0) normalized.setScale(0) else normalized
}

/**
 * Build the [PlateBreakdown] for a [targetInput] string against the [preferences]: an
 * un-parseable or non-positive target is [PlateBreakdown.Empty]; a target below the bar
 * is [PlateBreakdown.BelowBar]; otherwise the greedy split's per-side stack, total, and
 * shortfall are surfaced as [PlateBreakdown.Loadable].
 */
fun buildBreakdown(
    targetInput: String,
    preferences: PlateCalcPreferences,
): PlateBreakdown {
    val target = parseWeight(targetInput)?.takeIf { it.signum() > 0 } ?: return PlateBreakdown.Empty
    val result = splitPlates(target, preferences.barWeightDecimal(), preferences.inventorySlots())
    return if (result.belowBar) {
        PlateBreakdown.BelowBar(preferences.barWeightDecimal())
    } else {
        PlateBreakdown.Loadable(
            perSide = result.plates,
            barWeight = preferences.barWeightDecimal(),
            total = result.loadableWeight,
            shortfall = result.shortfall,
        )
    }
}

/** Parse a user-entered weight, tolerating a comma decimal separator; null when invalid. */
private fun parseWeight(input: String): BigDecimal? = input.trim().replace(',', '.').toBigDecimalOrNull()

/**
 * Format a kilogram weight for display without a trailing ".0" or padded zeros, e.g.
 * `20`, `2.5`, `1.25`. Used by the per-side stack, total, and shortfall lines.
 */
fun formatPlateKg(value: BigDecimal): String = value.stripTrailingZeros().toPlainString()
