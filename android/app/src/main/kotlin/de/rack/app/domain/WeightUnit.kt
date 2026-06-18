package de.rack.app.domain

import kotlin.math.roundToLong

/*
 * Pure kg <-> lb conversion (docs/specs/spec-settings.md). Weights are stored
 * canonically in kilograms (Phase 3 `set_logs.weight`); this utility converts
 * only at the display/entry boundary so the selected unit changes what the user
 * sees and types, never what is stored. No Compose, no Supabase: it lives in the
 * domain layer and is consumed by the repository/ViewModel so conversion stays
 * unit-testable independent of the UI.
 *
 * The factor and rounding increments are fixed by spec so the round-trip
 * (lb entry -> kg storage -> lb display) is deterministic: 1 kg = 2.2046226218 lb,
 * display rounds to 0.5 lb and kg entry rounds to 0.25 kg, and a round-trip
 * returns the entered value within one rounding step.
 */

/** The user-selectable weight unit. Storage is always kg; `LB` is display/entry only. */
enum class WeightUnit {
    KG,
    LB,
}

/** 1 kilogram in pounds (docs/specs/spec-settings.md prior decisions). */
const val KG_TO_LB: Double = 2.2046226218

/** Display rounding increment for pounds: 0.5 lb. */
const val LB_DISPLAY_INCREMENT: Double = 0.5

/** Entry rounding increment for kilograms: 0.25 kg. */
const val KG_ENTRY_INCREMENT: Double = 0.25

private fun roundToIncrement(
    value: Double,
    increment: Double,
): Double = (value / increment).roundToLong() * increment

/**
 * Converts a canonical kilogram [kg] to the value shown in [unit]. For [WeightUnit.KG]
 * the value is returned unchanged; for [WeightUnit.LB] it is converted to pounds and
 * rounded to [LB_DISPLAY_INCREMENT] (0.5 lb).
 */
fun kgToDisplay(
    kg: Double,
    unit: WeightUnit,
): Double =
    when (unit) {
        WeightUnit.KG -> kg
        WeightUnit.LB -> roundToIncrement(kg * KG_TO_LB, LB_DISPLAY_INCREMENT)
    }

/**
 * Converts a weight [entered] by the user in [unit] to the canonical kilogram value
 * to store. For [WeightUnit.KG] the entry is rounded to [KG_ENTRY_INCREMENT] (0.25 kg);
 * for [WeightUnit.LB] the pounds are converted to kilograms and then rounded to the
 * same kg increment, so what is stored is always a clean 0.25 kg step.
 */
fun displayToKg(
    entered: Double,
    unit: WeightUnit,
): Double {
    val kg =
        when (unit) {
            WeightUnit.KG -> entered
            WeightUnit.LB -> entered / KG_TO_LB
        }
    return roundToIncrement(kg, KG_ENTRY_INCREMENT)
}
