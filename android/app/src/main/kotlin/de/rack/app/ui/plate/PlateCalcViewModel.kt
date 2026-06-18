package de.rack.app.ui.plate

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.rack.app.data.PlateCalcRepository
import de.rack.app.domain.PlateBreakdown
import de.rack.app.domain.PlateCalcPreferences
import de.rack.app.domain.buildBreakdown
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The plate-calculator UI state: the editable [targetInput] (kg), the persisted
 * [preferences] (bar weight + inventory), and the derived [breakdown] the screen
 * renders. Everything weight-related is kilograms — Phase 13 is kg-only and never
 * reads the Phase-12 unit (docs/specs/spec-plate-calc-1rm.md).
 */
data class PlateCalcUiState(
    val targetInput: String,
    val preferences: PlateCalcPreferences,
) {
    /** The per-side stack / below-bar / shortfall / empty state for the current target. */
    val breakdown: PlateBreakdown get() = buildBreakdown(targetInput, preferences)
}

/**
 * Holds the plate-calculator state as a [StateFlow] and mediates every preference edit
 * through the [PlateCalcRepository] (#83, docs/specs/spec-plate-calc-1rm.md). The target
 * weight is transient UI input; the bar weight and inventory are persisted device-local
 * so they survive an app restart. The derived breakdown is recomputed by the pure math
 * via [PlateCalcUiState.breakdown] — no business logic lives in the Composables, which
 * observe [uiState] and emit edit intents only.
 */
class PlateCalcViewModel(
    private val repository: PlateCalcRepository,
    initialWeight: String,
) : ViewModel() {
    private val _uiState = MutableStateFlow(PlateCalcUiState(initialWeight, PlateCalcPreferences.DEFAULT))
    val uiState: StateFlow<PlateCalcUiState> = _uiState.asStateFlow()

    // True once the user has edited the prefs; a late initial load must not clobber the edit.
    private var edited = false

    init {
        viewModelScope.launch {
            // Load the persisted prefs once (first read provisions the defaults), then keep
            // optimistic in-memory state and persist each edit. A one-shot read — not a
            // continuous collect — and the [edited] guard so a late initial read on a slow
            // DataStore cannot overwrite an edit the user already made.
            val stored = repository.preferences.first()
            if (!edited) _uiState.update { it.copy(preferences = stored) }
        }
    }

    /** Update the transient target-weight input; the breakdown recomputes on read. */
    fun onTargetChange(value: String) = _uiState.update { it.copy(targetInput = value) }

    /** Adjust the persisted bar weight by [delta] kg, clamped to a sane range. */
    fun changeBarWeight(delta: Double) {
        val next = (currentPrefs().barWeightKg + delta).coerceIn(MIN_BAR_KG, MAX_BAR_KG)
        persist(currentPrefs().copy(barWeightKg = next))
    }

    /** Adjust the persisted pair count of the [plateKg] denomination by [delta]. */
    fun changePairCount(
        plateKg: Double,
        delta: Int,
    ) {
        val inventory =
            currentPrefs().inventory.map { stock ->
                if (stock.plateKg == plateKg) {
                    stock.copy(pairCount = (stock.pairCount + delta).coerceIn(MIN_PAIRS, MAX_PAIRS))
                } else {
                    stock
                }
            }
        persist(currentPrefs().copy(inventory = inventory))
    }

    private fun currentPrefs(): PlateCalcPreferences = _uiState.value.preferences

    /** Apply [prefs] to state immediately (so the breakdown reflects the edit) and persist them. */
    private fun persist(prefs: PlateCalcPreferences) {
        edited = true
        _uiState.update { it.copy(preferences = prefs) }
        viewModelScope.launch { repository.save(prefs) }
    }

    private companion object {
        const val MIN_BAR_KG = 5.0
        const val MAX_BAR_KG = 30.0
        const val MIN_PAIRS = 0
        const val MAX_PAIRS = 20
    }
}
