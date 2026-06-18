package de.rack.app.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Covers the pure kg <-> lb conversion (docs/specs/spec-settings.md): the fixed
 * factor (2.2046226218), 0.5 lb display rounding, 0.25 kg entry rounding, and the
 * round-trip guarantee (lb entry -> kg storage -> lb display returns the entered
 * value within one rounding step). No Compose, no Supabase.
 */
class WeightConversionTest {
    @Test
    fun `kg display in kg unit is unchanged`() {
        assertEquals(60.0, kgToDisplay(60.0, WeightUnit.KG))
        assertEquals(61.25, kgToDisplay(61.25, WeightUnit.KG))
    }

    @Test
    fun `kg display in lb unit converts and rounds to half a pound`() {
        // 61.25 kg * 2.2046226218 = 135.033... -> 135.0 lb at 0.5 lb steps.
        assertEquals(135.0, kgToDisplay(61.25, WeightUnit.LB))
        // 100 kg * 2.2046226218 = 220.462... -> 220.5 lb.
        assertEquals(220.5, kgToDisplay(100.0, WeightUnit.LB))
        // 0 kg stays 0 lb.
        assertEquals(0.0, kgToDisplay(0.0, WeightUnit.LB))
    }

    @Test
    fun `kg entry in kg unit rounds to a quarter kilogram`() {
        assertEquals(61.25, displayToKg(61.3, WeightUnit.KG))
        assertEquals(60.0, displayToKg(60.1, WeightUnit.KG))
        assertEquals(60.25, displayToKg(60.13, WeightUnit.KG))
    }

    @Test
    fun `lb entry converts to canonical kilograms rounded to a quarter kilogram`() {
        // 135 lb / 2.2046226218 = 61.2349... -> 61.25 kg.
        assertEquals(61.25, displayToKg(135.0, WeightUnit.LB))
        // 225 lb / 2.2046226218 = 102.058... -> 102.0 kg.
        assertEquals(102.0, displayToKg(225.0, WeightUnit.LB))
    }

    @Test
    fun `lb round-trip returns the entered pounds within one rounding step`() {
        val pounds = listOf(45.0, 95.0, 135.0, 185.0, 225.0, 315.0)
        for (entered in pounds) {
            val storedKg = displayToKg(entered, WeightUnit.LB)
            val shown = kgToDisplay(storedKg, WeightUnit.LB)
            assertTrue(
                kotlin.math.abs(shown - entered) <= LB_DISPLAY_INCREMENT,
                "round-trip for $entered lb returned $shown lb (stored $storedKg kg)",
            )
        }
    }

    @Test
    fun `kg round-trip is exact on quarter-kilogram values`() {
        val kilos = listOf(20.0, 42.5, 61.25, 100.0)
        for (kg in kilos) {
            assertEquals(kg, displayToKg(kgToDisplay(kg, WeightUnit.KG), WeightUnit.KG))
        }
    }
}
