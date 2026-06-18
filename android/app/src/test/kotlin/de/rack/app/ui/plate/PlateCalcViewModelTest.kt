package de.rack.app.ui.plate

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import de.rack.app.data.PlateCalcRepository
import de.rack.app.domain.PlateBreakdown
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Covers the [PlateCalcViewModel] wiring (#83, docs/specs/spec-plate-calc-1rm.md): the
 * target edit re-deriving the breakdown, and the bar-weight / pair-count edits persisting
 * through the repository within their clamps. The Main dispatcher is replaced so the
 * ViewModel's init collect and the persist coroutine run on the test scheduler.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlateCalcViewModelTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private val dispatcher = UnconfinedTestDispatcher()
    private val storeJob = SupervisorJob()
    private lateinit var store: DataStore<Preferences>

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        store =
            PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + storeJob)) {
                File(tempFolder.root, "vm.preferences_pb")
            }
    }

    @After
    fun tearDown() {
        storeJob.cancel()
        Dispatchers.resetMain()
    }

    private fun viewModel(initialWeight: String = "") = PlateCalcViewModel(PlateCalcRepository(store), initialWeight)

    @Test
    fun `target edit re-derives the breakdown`() {
        val vm = viewModel()

        vm.onTargetChange("100")

        val loadable = assertIs<PlateBreakdown.Loadable>(vm.uiState.value.breakdown)
        assertTrue(loadable.isExact)
        assertEquals(0, loadable.total.compareTo(BigDecimal("100")))
    }

    @Test
    fun `initial weight seeds the target`() {
        val vm = viewModel(initialWeight = "60")

        assertEquals("60", vm.uiState.value.targetInput)
        assertIs<PlateBreakdown.Loadable>(vm.uiState.value.breakdown)
    }

    @Test
    fun `bar weight edit persists and clamps to the minimum`() {
        val vm = viewModel()
        // Defaults to 20 kg; six 2.5 kg decrements would reach 5, a seventh clamps at 5.
        repeat(7) { vm.changeBarWeight(-2.5) }

        assertEquals(5.0, vm.uiState.value.preferences.barWeightKg)
        // Await the persisted write (DataStore commits on its own IO actor).
        val persisted = runBlocking { PlateCalcRepository(store).preferences.first { it.barWeightKg == 5.0 } }
        assertEquals(5.0, persisted.barWeightKg)
    }

    @Test
    fun `pair count edit persists and clamps at zero`() {
        val vm = viewModel()

        vm.changePairCount(plateKg = 25.0, delta = -5)

        val stock = vm.uiState.value.preferences.inventory.first { it.plateKg == 25.0 }
        assertEquals(0, stock.pairCount)
        // Await the persisted write: the 25 kg denomination drops to 0 pairs.
        val persisted =
            runBlocking {
                PlateCalcRepository(store)
                    .preferences
                    .first { prefs -> prefs.inventory.first { it.plateKg == 25.0 }.pairCount == 0 }
            }
        assertEquals(0, persisted.inventory.first { it.plateKg == 25.0 }.pairCount)
    }
}
