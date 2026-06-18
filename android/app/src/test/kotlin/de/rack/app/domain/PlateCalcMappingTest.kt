package de.rack.app.domain

import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Covers the pure bridge from persisted prefs to the plate-math breakdown the
 * calculator UI renders (#83, docs/specs/spec-plate-calc-1rm.md): the below-bar,
 * exact, short, and empty states; the pair-count inventory mapping; and the
 * display formatting of kg values.
 */
class PlateCalcMappingTest {
    private val defaults = PlateCalcPreferences.DEFAULT

    @Test
    fun `blank or non-positive target is the empty state`() {
        assertIs<PlateBreakdown.Empty>(buildBreakdown("", defaults))
        assertIs<PlateBreakdown.Empty>(buildBreakdown("  ", defaults))
        assertIs<PlateBreakdown.Empty>(buildBreakdown("0", defaults))
        assertIs<PlateBreakdown.Empty>(buildBreakdown("abc", defaults))
    }

    @Test
    fun `target below the bar is the below-bar state`() {
        val breakdown = buildBreakdown("15", defaults)

        val belowBar = assertIs<PlateBreakdown.BelowBar>(breakdown)
        assertEquals(0, belowBar.barWeight.compareTo(BigDecimal("20")))
    }

    @Test
    fun `exact target yields the per-side stack with no shortfall`() {
        val breakdown = buildBreakdown("100", defaults)

        val loadable = assertIs<PlateBreakdown.Loadable>(breakdown)
        assertTrue(loadable.isExact)
        assertEquals(listOf(PlateSlot(BigDecimal("25"), 1), PlateSlot(BigDecimal("15"), 1)), loadable.perSide)
        assertEquals(0, loadable.total.compareTo(BigDecimal("100")))
    }

    @Test
    fun `unreachable target reports the closest loadable weight and the shortfall`() {
        // 103 -> 102.5 loadable, 0.5 short (see PlateMathTest).
        val breakdown = buildBreakdown("103", defaults)

        val loadable = assertIs<PlateBreakdown.Loadable>(breakdown)
        assertTrue(!loadable.isExact)
        assertEquals(0, loadable.total.compareTo(BigDecimal("102.5")))
        assertEquals(0, loadable.shortfall.compareTo(BigDecimal("0.5")))
    }

    @Test
    fun `inventory pair counts cap the greedy split`() {
        // Only one pair of 25, the rest unavailable -> 60 kg loads 25 per side then runs out.
        val prefs =
            PlateCalcPreferences(
                barWeightKg = 20.0,
                inventory =
                    listOf(
                        PlateStock(plateKg = 25.0, pairCount = 1),
                        PlateStock(plateKg = 20.0, pairCount = 0),
                    ),
            )

        val loadable = assertIs<PlateBreakdown.Loadable>(buildBreakdown("100", prefs))
        assertEquals(listOf(PlateSlot(BigDecimal("25"), 1)), loadable.perSide)
        assertEquals(0, loadable.total.compareTo(BigDecimal("70")))
        assertEquals(0, loadable.shortfall.compareTo(BigDecimal("30")))
    }

    @Test
    fun `a comma decimal separator is parsed`() {
        val loadable = assertIs<PlateBreakdown.Loadable>(buildBreakdown("47,5", defaults))

        assertTrue(loadable.isExact)
        assertEquals(0, loadable.total.compareTo(BigDecimal("47.5")))
    }

    @Test
    fun `kg formatting strips trailing zeros`() {
        assertEquals("20", formatPlateKg(BigDecimal("20")))
        assertEquals("2.5", formatPlateKg(BigDecimal("2.50")))
        assertEquals("1.25", formatPlateKg(BigDecimal("1.25")))
        assertEquals("0", formatPlateKg(BigDecimal.ZERO))
    }
}
