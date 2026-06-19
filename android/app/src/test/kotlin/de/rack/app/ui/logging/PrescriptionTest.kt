package de.rack.app.ui.logging

import de.rack.app.domain.PlanExercise
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Covers the pure typed-prescription formatters (#194,
 * docs/specs/spec-structured-prescription.md): the rep / RIR ranges collapse to a
 * single value when both bounds match, the rest renders only when positive, and
 * [prescriptionLabel] joins the populated parts (or is null when none are set).
 */
class PrescriptionTest {
    @Test
    fun `rep range collapses equal bounds and drops a missing side`() {
        assertEquals("6–8", repRangeText(6, 8))
        assertEquals("8", repRangeText(8, 8))
        assertEquals("8", repRangeText(8, null))
        assertEquals("8", repRangeText(null, 8))
        assertEquals("", repRangeText(null, null))
    }

    @Test
    fun `rir range is prefixed and empty when unset`() {
        assertEquals("RIR 1–2", rirRangeText(1, 2))
        assertEquals("RIR 1", rirRangeText(1, 1))
        assertEquals("", rirRangeText(null, null))
    }

    @Test
    fun `rest renders only when positive`() {
        assertEquals("120s", restText(120))
        assertEquals("", restText(0))
        assertEquals("", restText(null))
    }

    @Test
    fun `sets and reps join with the multiplication sign`() {
        assertEquals("3 × 6–8", setsRepsText(3, 6, 8))
        assertEquals("3", setsRepsText(3, null, null))
        assertEquals("6–8", setsRepsText(null, 6, 8))
        assertEquals("", setsRepsText(null, null, null))
    }

    @Test
    fun `prescription label joins the populated parts`() {
        assertEquals(
            "3 × 6–8 · RIR 1–2 · 120s",
            prescriptionLabel(base.copy(sets = 3, repMin = 6, repMax = 8, rirLow = 1, rirHigh = 2, restSeconds = 120)),
        )
        assertEquals("3 × 8", prescriptionLabel(base.copy(sets = 3, repMin = 8, repMax = 8)))
    }

    @Test
    fun `prescription label is null with no typed fields`() {
        assertNull(prescriptionLabel(base))
    }

    private val base =
        PlanExercise(
            id = "p",
            dayId = "d",
            exerciseId = "e",
            name = "e",
            category = null,
            position = 0,
            sets = null,
            repMin = null,
            repMax = null,
            rirLow = null,
            rirHigh = null,
            restSeconds = null,
            cue = null,
            supersetId = null,
            groupType = null,
        )
}
