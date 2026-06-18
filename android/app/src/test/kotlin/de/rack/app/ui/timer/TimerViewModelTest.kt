package de.rack.app.ui.timer

import de.rack.app.data.TimerController
import de.rack.app.domain.ExerciseType
import de.rack.app.domain.PlanExercise
import de.rack.app.domain.REST_CIRCUIT_SECONDS
import de.rack.app.domain.REST_COMPOUND_SECONDS
import de.rack.app.domain.REST_ISOLATION_SECONDS
import de.rack.app.domain.REST_SUPERSET_SECONDS
import de.rack.app.ui.plan.LoggedExerciseContext
import de.rack.app.ui.theme.SupersetKind
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

    @Test
    fun `logging a standalone compound set auto-starts the session and a compound rest with no cue`() {
        viewModel.onSetLogged(category = "Legs", context = context(group = listOf(exercise("a")), index = 0))

        val state = viewModel.uiState.value
        assertNotNull(state.session)
        assertEquals(REST_COMPOUND_SECONDS, state.rest?.remainingSeconds)
        assertNull(state.rotation)
    }

    @Test
    fun `logging a standalone isolation set rests at the isolation default`() {
        viewModel.onSetLogged(category = "Arms", context = context(group = listOf(exercise("a")), index = 0))

        assertEquals(REST_ISOLATION_SECONDS, viewModel.uiState.value.rest?.remainingSeconds)
    }

    @Test
    fun `logging a superset member rests at the superset default and cues the other member`() {
        val group = listOf(exercise("a", "Press"), exercise("b", "Row"))

        viewModel.onSetLogged(category = "Chest", context = context(group, index = 0))

        val state = viewModel.uiState.value
        assertEquals(REST_SUPERSET_SECONDS, state.rest?.remainingSeconds)
        assertEquals(SupersetKind.SUPERSET, state.rotation?.kind)
        assertEquals("Row", state.rotation?.nextExerciseName)
    }

    @Test
    fun `logging a circuit member rests at the circuit default and cues the next station`() {
        val group = listOf(exercise("a", "A"), exercise("b", "B"), exercise("c", "C"))

        viewModel.onSetLogged(category = "Back", context = context(group, index = 2))

        val state = viewModel.uiState.value
        assertEquals(REST_CIRCUIT_SECONDS, state.rest?.remainingSeconds)
        assertEquals(SupersetKind.CIRCUIT, state.rotation?.kind)
        assertEquals("A", state.rotation?.nextExerciseName) // wraps to the first station
    }

    @Test
    fun `a second logged set keeps the same session running`() {
        viewModel.onSetLogged(category = "Legs", context = context(listOf(exercise("a")), index = 0))
        advance(60)

        viewModel.onSetLogged(category = "Arms", context = context(listOf(exercise("b")), index = 0))

        val state = viewModel.uiState.value
        assertEquals(60, state.session?.elapsedSeconds)
        assertEquals(REST_ISOLATION_SECONDS, state.rest?.remainingSeconds)
    }

    @Test
    fun `ticking a standalone compound set auto-starts the session and a compound rest`() {
        viewModel.onSetTicked(type = ExerciseType.COMPOUND, context = context(listOf(exercise("a")), index = 0))

        val state = viewModel.uiState.value
        assertNotNull(state.session)
        assertEquals(REST_COMPOUND_SECONDS, state.rest?.remainingSeconds)
        assertNull(state.rotation)
    }

    @Test
    fun `ticking a standalone isolation set rests at the isolation default`() {
        viewModel.onSetTicked(type = ExerciseType.ISOLATION, context = context(listOf(exercise("a")), index = 0))

        assertEquals(REST_ISOLATION_SECONDS, viewModel.uiState.value.rest?.remainingSeconds)
    }

    @Test
    fun `the superset group overrides a ticked compound to the superset default`() {
        val group = listOf(exercise("a", "Press"), exercise("b", "Row"))

        viewModel.onSetTicked(type = ExerciseType.COMPOUND, context = context(group, index = 0))

        val state = viewModel.uiState.value
        assertEquals(REST_SUPERSET_SECONDS, state.rest?.remainingSeconds)
        assertEquals(SupersetKind.SUPERSET, state.rotation?.kind)
        assertEquals("Row", state.rotation?.nextExerciseName)
    }

    @Test
    fun `the circuit group overrides a ticked compound to the circuit default`() {
        val group = listOf(exercise("a"), exercise("b"), exercise("c"))

        viewModel.onSetTicked(type = ExerciseType.COMPOUND, context = context(group, index = 0))

        assertEquals(REST_CIRCUIT_SECONDS, viewModel.uiState.value.rest?.remainingSeconds)
    }

    private fun context(
        group: List<PlanExercise>,
        index: Int,
    ) = LoggedExerciseContext(group = group, index = index)

    private fun exercise(
        id: String,
        name: String = id,
    ) = PlanExercise(
        id = id,
        dayId = "day",
        exerciseId = id,
        name = name,
        category = null,
        position = 0,
        target = null,
        rir = null,
        cue = null,
        supersetLabel = null,
    )
}
