package de.rack.app.domain

/**
 * The device-local plate-calculator preferences (issue #82,
 * docs/specs/spec-plate-calc-1rm.md): the configurable [barWeightKg] and the
 * [inventory] of plate denominations the lifter owns.
 *
 * These are derived UI preferences, single-device, with no agent involvement, so
 * they persist via Jetpack DataStore behind [de.rack.app.data.PlateCalcRepository]
 * — never Supabase, no table, no migration, no network call. Weights are kept in
 * kilograms (the only unit this phase supports); the greedy plate split (#81)
 * consumes this model unchanged.
 */
data class PlateCalcPreferences(
    val barWeightKg: Double,
    val inventory: List<PlateStock>,
) {
    companion object {
        /** The standard Olympic bar weight, the default until the lifter changes it. */
        const val DEFAULT_BAR_WEIGHT_KG: Double = 20.0

        /** One pair per denomination — the default count for a freshly stocked rack. */
        const val DEFAULT_PAIR_COUNT: Int = 1

        // The canonical kg plate set (kg), largest first; each treated as available
        // in pairs. These exact values round-trip through DataStore without drift.
        private val DEFAULT_PLATE_KG = listOf(25.0, 20.0, 15.0, 10.0, 5.0, 2.5, 1.25)

        /** The default preferences provisioned on first read: 20 kg bar, one pair each. */
        val DEFAULT =
            PlateCalcPreferences(
                barWeightKg = DEFAULT_BAR_WEIGHT_KG,
                inventory = DEFAULT_PLATE_KG.map { PlateStock(plateKg = it, pairCount = DEFAULT_PAIR_COUNT) },
            )
    }
}

/**
 * One plate denomination the lifter owns: [plateKg] is a single plate's weight and
 * [pairCount] how many pairs are available (one plate per side per pair). The greedy
 * split (#81) consumes at most [pairCount] of this denomination.
 */
data class PlateStock(
    val plateKg: Double,
    val pairCount: Int,
)
