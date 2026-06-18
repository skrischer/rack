package de.rack.app.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import de.rack.app.domain.PlateCalcPreferences
import de.rack.app.domain.PlateStock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * The single access point for the device-local plate-calculator preferences (#82,
 * docs/specs/spec-plate-calc-1rm.md): the configurable bar weight and plate
 * inventory, persisted via Jetpack DataStore so they survive an app restart.
 *
 * These are derived UI preferences with no agent involvement, so they never touch
 * Supabase — no table, no migration, no MCP tool, no network call. No Composable
 * reads DataStore directly; the plate-calculator ViewModel (#83) consumes this.
 * Missing keys fall back to [PlateCalcPreferences.DEFAULT] (20 kg bar, the standard
 * kg plate set in pairs), so a first read on a fresh install yields the defaults.
 */
class PlateCalcRepository(
    private val dataStore: DataStore<Preferences>,
    private val json: Json = Json,
) {
    private val inventorySerializer = ListSerializer(PlateStockEntry.serializer())

    /** The persisted preferences, defaulting to [PlateCalcPreferences.DEFAULT] when unset. */
    val preferences: Flow<PlateCalcPreferences> = dataStore.data.map(::toPreferences)

    /** Persist [prefs], replacing the bar weight and the full inventory. */
    suspend fun save(prefs: PlateCalcPreferences) {
        dataStore.edit { store ->
            store[BAR_WEIGHT_KEY] = prefs.barWeightKg
            store[INVENTORY_KEY] = json.encodeToString(inventorySerializer, prefs.inventory.map(PlateStockEntry::from))
        }
    }

    private fun toPreferences(store: Preferences): PlateCalcPreferences =
        PlateCalcPreferences(
            barWeightKg = store[BAR_WEIGHT_KEY] ?: PlateCalcPreferences.DEFAULT.barWeightKg,
            inventory = store[INVENTORY_KEY]?.let(::decodeInventory) ?: PlateCalcPreferences.DEFAULT.inventory,
        )

    private fun decodeInventory(stored: String): List<PlateStock> =
        json.decodeFromString(inventorySerializer, stored).map(PlateStockEntry::toDomain)

    private companion object {
        val BAR_WEIGHT_KEY = doublePreferencesKey("plate_calc_bar_weight_kg")
        val INVENTORY_KEY = stringPreferencesKey("plate_calc_inventory")
    }
}

/** The JSON-serializable form of [PlateStock] persisted in the inventory preference. */
@Serializable
private data class PlateStockEntry(
    val plateKg: Double,
    val pairCount: Int,
) {
    fun toDomain() = PlateStock(plateKg = plateKg, pairCount = pairCount)

    companion object {
        fun from(stock: PlateStock) = PlateStockEntry(plateKg = stock.plateKg, pairCount = stock.pairCount)
    }
}
