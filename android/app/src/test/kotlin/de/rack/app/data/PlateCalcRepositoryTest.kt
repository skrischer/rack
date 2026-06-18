package de.rack.app.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import de.rack.app.domain.PlateCalcPreferences
import de.rack.app.domain.PlateStock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.assertEquals

/**
 * Round-trip persistence coverage for [PlateCalcRepository] (#82,
 * docs/specs/spec-plate-calc-1rm.md). The file-backed DataStore is created over a
 * temp file, written through one repository, then re-opened with a fresh DataStore
 * + repository instance to simulate an app restart (a new process re-reads the same
 * file), proving the bar weight and inventory survive unchanged. Each store runs on
 * its own scope that is cancelled before the file is re-opened, so no two live
 * DataStores share the file.
 */
class PlateCalcRepositoryTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `first read on a fresh store returns the defaults`() =
        runTest {
            val read = withStore(File(tempFolder.root, "fresh.preferences_pb")) { it.preferences.first() }

            assertEquals(PlateCalcPreferences.DEFAULT, read)
        }

    @Test
    fun `saved bar weight and inventory survive an app restart`() =
        runTest {
            val file = File(tempFolder.root, "round_trip.preferences_pb")
            val saved =
                PlateCalcPreferences(
                    barWeightKg = 15.0,
                    inventory =
                        listOf(
                            PlateStock(plateKg = 20.0, pairCount = 3),
                            PlateStock(plateKg = 2.5, pairCount = 1),
                            PlateStock(plateKg = 1.25, pairCount = 2),
                        ),
                )

            withStore(file) { it.save(saved) }
            // A fresh DataStore + repository over the same file models a process restart.
            val reloaded = withStore(file) { it.preferences.first() }

            assertEquals(saved, reloaded)
        }

    private suspend fun <T> withStore(
        file: File,
        block: suspend (PlateCalcRepository) -> T,
    ): T {
        val job = SupervisorJob()
        val store: DataStore<Preferences> =
            PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + job)) { file }
        return try {
            block(PlateCalcRepository(store))
        } finally {
            // Join so DataStore releases the file from its active-files registry
            // before the same file is re-opened to model the restart.
            job.cancelAndJoin()
        }
    }
}
