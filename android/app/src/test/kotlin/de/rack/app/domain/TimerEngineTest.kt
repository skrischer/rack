package de.rack.app.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Covers the pure timer-engine math (docs/specs/spec-timers.md): the drift-free
 * countdown computation, the +15/-15/skip/restart transitions with the
 * clamp-at-zero rule, and the count-up session elapsed. All times are in the
 * engine's elapsed-realtime milliseconds; a fixed base instant keeps assertions
 * independent of any real clock.
 */
class TimerEngineTest {
    private val base = 1_000_000L
    private val second = 1000L

    @Test
    fun `rest remaining is recomputed from the end-instant against now (no drift)`() {
        val rest = RestTimer.start(durationSeconds = 60, nowMs = base)

        assertEquals(60, rest.remainingSeconds(base))
        assertEquals(50, rest.remainingSeconds(base + 10 * second))
        // Simulate a long background gap: remaining reflects real elapsed time.
        assertEquals(1, rest.remainingSeconds(base + 59 * second))
    }

    @Test
    fun `rest remaining clamps to zero once the end-instant has passed`() {
        val rest = RestTimer.start(durationSeconds = 30, nowMs = base)

        assertEquals(0, rest.remainingSeconds(base + 30 * second))
        assertEquals(0, rest.remainingSeconds(base + 120 * second))
    }

    @Test
    fun `rest is finished only at or after the end-instant`() {
        val rest = RestTimer.start(durationSeconds = 30, nowMs = base)

        assertFalse(rest.isFinished(base + 29 * second))
        assertTrue(rest.isFinished(base + 30 * second))
        assertTrue(rest.isFinished(base + 31 * second))
    }

    @Test
    fun `add step pushes the end-instant out by fifteen seconds`() {
        val rest = RestTimer.start(durationSeconds = 60, nowMs = base).addStep()

        assertEquals(75, rest.remainingSeconds(base))
    }

    @Test
    fun `subtract step removes fifteen seconds of remaining`() {
        val rest = RestTimer.start(durationSeconds = 60, nowMs = base).subtractStep(base)

        assertEquals(45, rest.remainingSeconds(base))
    }

    @Test
    fun `subtracting past zero clamps the rest to finished, never negative`() {
        val rest = RestTimer.start(durationSeconds = 10, nowMs = base).subtractStep(base)

        assertEquals(0, rest.remainingSeconds(base))
        assertTrue(rest.isFinished(base))
    }

    @Test
    fun `skip ends the rest now so remaining reads zero immediately`() {
        val rest = RestTimer.start(durationSeconds = 90, nowMs = base).skip(base)

        assertEquals(0, rest.remainingSeconds(base))
        assertTrue(rest.isFinished(base))
    }

    @Test
    fun `restart re-anchors a fresh full duration from the restart instant`() {
        val original = RestTimer.start(durationSeconds = 90, nowMs = base)
        val later = base + 80 * second
        val restarted = RestTimer.start(durationSeconds = 90, nowMs = later)

        assertEquals(10, original.remainingSeconds(later))
        assertEquals(90, restarted.remainingSeconds(later))
    }

    @Test
    fun `session elapsed counts up from the start-instant against now`() {
        val session = SessionTimer.start(nowMs = base)

        assertEquals(0, session.elapsedSeconds(base))
        assertEquals(42, session.elapsedSeconds(base + 42 * second))
        assertEquals(3600, session.elapsedSeconds(base + 3600 * second))
    }
}
