package de.rack.app.domain

import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Covers the pure plate-math and Epley 1RM functions (docs/specs/spec-plate-calc-1rm.md):
 * the greedy largest-first per-side split with pair-counted, inventory-limited
 * plates, the below-bar and shortfall states, the float-drift-free 2.5/1.25 kg
 * cases, and the Epley estimate with its reps=1, known case, suppression, and
 * rounding boundaries.
 */
class PlateMathTest {
    private fun kg(value: String) = BigDecimal(value)

    @Test
    fun `target equal to the bar loads no plates and has no shortfall`() {
        val result = splitPlates(kg("20"))

        assertTrue(result.plates.isEmpty())
        assertEquals(0, result.shortfall.compareTo(BigDecimal.ZERO))
        assertEquals(0, result.loadableWeight.compareTo(kg("20")))
        assertEquals(false, result.belowBar)
    }

    @Test
    fun `exact hit splits across several plate sizes per side`() {
        val result = splitPlates(kg("100"))

        // 100 = bar 20 + 80, 40 per side -> 25 + 15.
        assertEquals(listOf(PlateSlot(kg("25"), 1), PlateSlot(kg("15"), 1)), result.plates)
        assertEquals(0, result.shortfall.compareTo(BigDecimal.ZERO))
        assertEquals(0, result.loadableWeight.compareTo(kg("100")))
    }

    @Test
    fun `repeated largest plate is reported as a single pair count`() {
        val result = splitPlates(kg("110"))

        // 110 = bar 20 + 90, 45 per side -> 25 + 20.
        assertEquals(listOf(PlateSlot(kg("25"), 1), PlateSlot(kg("20"), 1)), result.plates)
        assertEquals(0, result.shortfall.compareTo(BigDecimal.ZERO))
    }

    @Test
    fun `unreachable target returns the closest weight at or below it with a shortfall`() {
        // 103 -> bar 20 + 83, 41.5 per side. Greedy: 25 + 15 + 1.25 = 41.25 per side
        // -> loadable 102.5, short 0.5.
        val result = splitPlates(kg("103"))

        assertTrue(result.loadableWeight <= kg("103"))
        assertEquals(0, result.loadableWeight.compareTo(kg("102.5")))
        assertEquals(0, result.shortfall.compareTo(kg("0.5")))
    }

    @Test
    fun `target below the bar weight is the below-bar state with no plates`() {
        val result = splitPlates(kg("15"))

        assertTrue(result.belowBar)
        assertTrue(result.plates.isEmpty())
        assertEquals(0, result.loadableWeight.compareTo(kg("15")))
        assertEquals(0, result.shortfall.compareTo(BigDecimal.ZERO))
    }

    @Test
    fun `empty inventory loads only the bar and reports the full shortfall`() {
        val result = splitPlates(kg("60"), inventory = emptyList())

        assertTrue(result.plates.isEmpty())
        assertEquals(0, result.loadableWeight.compareTo(kg("20")))
        assertEquals(0, result.shortfall.compareTo(kg("40")))
    }

    @Test
    fun `an inventory-limited plate runs out mid-greedy and the next size fills in`() {
        // One pair of 25 only, unlimited 20s. 140 = bar 20 + 120, 60 per side.
        // 25 (limited to 1 pair) + 20 + 15 = 60 per side, exact.
        val inventory =
            listOf(
                PlateSlot(kg("25"), 1),
                PlateSlot(kg("20"), Int.MAX_VALUE),
                PlateSlot(kg("15"), Int.MAX_VALUE),
            )
        val result = splitPlates(kg("140"), inventory = inventory)

        assertEquals(
            listOf(PlateSlot(kg("25"), 1), PlateSlot(kg("20"), 1), PlateSlot(kg("15"), 1)),
            result.plates,
        )
        assertEquals(0, result.shortfall.compareTo(BigDecimal.ZERO))
    }

    @Test
    fun `fine plates produce no float drift`() {
        // 47.5 = bar 20 + 27.5, 13.75 per side -> 10 + 2.5 + 1.25, exact.
        val result = splitPlates(kg("47.5"))

        assertEquals(
            listOf(PlateSlot(kg("10"), 1), PlateSlot(kg("2.5"), 1), PlateSlot(kg("1.25"), 1)),
            result.plates,
        )
        assertEquals(0, result.shortfall.compareTo(BigDecimal.ZERO))
        assertEquals(0, result.loadableWeight.compareTo(kg("47.5")))
    }

    @Test
    fun `epley at one rep returns the lifted weight`() {
        assertEquals(0, epleyOneRepMax(kg("100"), 1)!!.compareTo(kg("100.0")))
    }

    @Test
    fun `epley for one hundred by five is about one sixteen point seven`() {
        assertEquals(0, epleyOneRepMax(kg("100"), 5)!!.compareTo(kg("116.7")))
    }

    @Test
    fun `epley rounds to one decimal`() {
        // 102.5 * (1 + 8/30) = 129.833... -> 129.8.
        assertEquals(0, epleyOneRepMax(kg("102.5"), 8)!!.compareTo(kg("129.8")))
    }

    @Test
    fun `epley is suppressed for zero reps`() {
        assertNull(epleyOneRepMax(kg("100"), 0))
    }

    @Test
    fun `epley is suppressed for zero or blank weight`() {
        assertNull(epleyOneRepMax(kg("0"), 5))
        assertNull(epleyOneRepMax(null, 5))
    }
}
