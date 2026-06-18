package de.rack.app.domain

import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Covers the heaviest-set 1RM selector (docs/specs/spec-plate-calc-1rm.md, #84):
 * picking the max-weight set with ties broken by higher reps, the single-rep and
 * suppression edge cases, and that zero-rep entries are ignored.
 */
class OneRepMaxTest {
    private fun log(
        id: String,
        weight: Double?,
        reps: List<Int>,
    ) = SetLog(
        id = id,
        planExerciseId = "ex",
        date = "2026-06-17",
        weight = weight,
        reps = reps,
        rir = null,
        loggedAt = "2026-06-17T10:00:00Z",
    )

    @Test
    fun `empty history has no estimate`() {
        assertNull(heaviestSetOneRepMax(emptyList()))
    }

    @Test
    fun `the heaviest set drives the estimate`() {
        val history =
            listOf(
                log("a", 80.0, listOf(8, 8)),
                log("b", 100.0, listOf(5)),
            )

        // 100 x 5 -> 116.7 by Epley.
        assertEquals(0, heaviestSetOneRepMax(history)!!.compareTo(BigDecimal("116.7")))
    }

    @Test
    fun `at equal weight the higher rep count wins`() {
        val history = listOf(log("a", 100.0, listOf(3, 6, 2)))

        // 100 x 6 -> 120.0 beats the 3-rep and 2-rep candidates at the same weight.
        assertEquals(0, heaviestSetOneRepMax(history)!!.compareTo(BigDecimal("120.0")))
    }

    @Test
    fun `a single rep at the heaviest weight returns the lifted weight`() {
        val history =
            listOf(
                log("a", 100.0, listOf(5)),
                log("b", 120.0, listOf(1)),
            )

        assertEquals(0, heaviestSetOneRepMax(history)!!.compareTo(BigDecimal("120.0")))
    }

    @Test
    fun `a zero-rep heaviest set is ignored in favour of a lighter logged set`() {
        val history =
            listOf(
                log("a", 120.0, listOf(0)),
                log("b", 80.0, listOf(5)),
            )

        // 80 x 5 -> 93.3; the 120 kg entry has no usable rep.
        assertEquals(0, heaviestSetOneRepMax(history)!!.compareTo(BigDecimal("93.3")))
    }

    @Test
    fun `history with only zero or blank entries is suppressed`() {
        val history =
            listOf(
                log("a", 0.0, listOf(5)),
                log("b", 100.0, listOf(0)),
                log("c", null, listOf(8)),
            )

        assertNull(heaviestSetOneRepMax(history))
    }
}
