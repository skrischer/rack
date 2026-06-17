package de.rack.app.ui.timer

import de.rack.app.data.TimerController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.After
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Covers the [TimerViewModel] control routing and the spec's central lifecycle
 * invariant: a rest reaching zero or being skipped never stops the count-up
 * session timer (docs/specs/spec-timers.md "Service lifecycle"). A mutable fake
 * clock stands in for the elapsed-realtime source so transitions are asserted
 * deterministically; the controller's tick loop runs on a test dispatcher scope so
 * its parked launch needs no Android looper. Every assertion follows a control call
 * that refreshes synchronously, so it observes the advanced clock immediately.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TimerViewModelTest {
    private var now = 1_000_000L
    private val scope = CoroutineScope(StandardTestDispatcher())
    private val viewModel = TimerViewModel(TimerController(clock = { now }, scope = scope))

    @After
    fun tearDown() {
        scope.cancel()
    }

    private fun advance(seconds: Int) {
        now += seconds * 1000L
    }

    @Test
    fun `starting a rest exposes the full duration remaining`() {
        viewModel.startRest(150)

        assertEquals(150, viewModel.uiState.value.rest?.remainingSeconds)
        assertFalse(viewModel.uiState.value.rest?.finished == true)
    }

    @Test
    fun `add and subtract step the remaining rest by fifteen seconds`() {
        viewModel.startRest(75)

        viewModel.addRest()
        assertEquals(90, viewModel.uiState.value.rest?.remainingSeconds)

        viewModel.subtractRest()
        viewModel.subtractRest()
        assertEquals(60, viewModel.uiState.value.rest?.remainingSeconds)
    }

    @Test
    fun `subtracting past zero clamps the rest remaining to zero`() {
        viewModel.startRest(10)

        viewModel.subtractRest()

        assertEquals(0, viewModel.uiState.value.rest?.remainingSeconds)
        assertTrue(viewModel.uiState.value.rest?.finished == true)
    }

    @Test
    fun `skip ends the rest immediately`() {
        viewModel.startRest(90)

        viewModel.skipRest()

        assertEquals(0, viewModel.uiState.value.rest?.remainingSeconds)
        assertTrue(viewModel.uiState.value.rest?.finished == true)
    }

    @Test
    fun `restart re-anchors the rest at its original duration`() {
        viewModel.startRest(90)
        advance(80)

        viewModel.restartRest()

        assertEquals(90, viewModel.uiState.value.rest?.remainingSeconds)
    }

    @Test
    fun `session counts up from start and stops only on explicit stop`() {
        viewModel.startSession()
        assertEquals(0, viewModel.uiState.value.session?.elapsedSeconds)

        advance(120)
        viewModel.startRest(30) // any control refresh reflects the advanced clock
        assertEquals(120, viewModel.uiState.value.session?.elapsedSeconds)

        viewModel.stopSession()
        assertNull(viewModel.uiState.value.session)
    }

    @Test
    fun `ending a rest leaves the session timer running`() {
        viewModel.startSession()
        viewModel.startRest(30)
        advance(40) // the rest is well past its end-instant

        viewModel.skipRest()

        val state = viewModel.uiState.value
        assertNotNull(state.session)
        assertEquals(40, state.session?.elapsedSeconds)
        assertEquals(0, state.rest?.remainingSeconds)
        assertTrue(state.rest?.finished == true)
    }
}
