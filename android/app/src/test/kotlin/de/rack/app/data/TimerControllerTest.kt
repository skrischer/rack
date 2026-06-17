package de.rack.app.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.After
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Covers the [TimerController] state that the foreground service binds to (issue
 * #54, docs/specs/spec-timers.md "Service lifecycle"): [TimerController.isSessionActive]
 * tracks exactly the start..explicit-stop window the service lives across, and
 * [TimerController.restFinished] emits once when a rest crosses zero (driving the
 * completion alert) but not when the rest is skipped. A mutable fake clock and a
 * test-dispatcher scope keep the transitions deterministic without Android; an
 * unconfined collector scope drains the hot completion flow eagerly.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TimerControllerTest {
    private var now = 1_000_000L
    private val scope = CoroutineScope(StandardTestDispatcher())
    private val collectorScope = CoroutineScope(UnconfinedTestDispatcher())
    private val controller = TimerController(clock = { now }, scope = scope)
    private val finishedEvents = mutableListOf<Unit>()

    init {
        controller.restFinished
            .onEach { finishedEvents.add(Unit) }
            .launchIn(collectorScope)
    }

    @After
    fun tearDown() {
        scope.cancel()
        collectorScope.cancel()
    }

    private fun advance(seconds: Int) {
        now += seconds * 1000L
    }

    @Test
    fun `session active flips on start and only off on explicit stop`() {
        assertFalse(controller.isSessionActive.value)

        controller.startSession()
        assertTrue(controller.isSessionActive.value)

        controller.startRest(30)
        advance(40)
        controller.skipRest() // a rest ending must not deactivate the session
        assertTrue(controller.isSessionActive.value)

        controller.stopSession()
        assertFalse(controller.isSessionActive.value)
    }

    @Test
    fun `rest finishing emits exactly one completion event`() {
        controller.startSession()
        controller.startRest(10)

        // Subtract past zero: the end-instant clamps to now, so the next refresh
        // observes a finished rest and emits. A second refresh of the same finished
        // rest must not re-emit (one alert per rest).
        controller.subtractRest()
        controller.subtractRest()

        assertEquals(1, finishedEvents.size)
    }

    @Test
    fun `skipping a rest does not emit a completion event`() {
        controller.startSession()
        controller.startRest(30)
        controller.skipRest()

        assertTrue(finishedEvents.isEmpty())
    }
}
