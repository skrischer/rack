package de.rack.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.rack.app.data.SettingsRepository
import de.rack.app.domain.UserSettings
import de.rack.app.domain.WeightUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Settings UI state: loading, the loaded settings, or an error the screen can retry. */
sealed interface SettingsUiState {
    data object Loading : SettingsUiState

    data class Content(val settings: UserSettings) : SettingsUiState

    data class Error(val message: String) : SettingsUiState
}

/** The four per-type rest-timer defaults the user can edit (docs/specs/spec-settings.md). */
enum class RestDefaultKind {
    COMPOUND,
    ISOLATION,
    SUPERSET,
    CIRCUIT,
}

/**
 * Exposes the signed-in user's [UserSettings] as a [StateFlow] and mediates every
 * update through the [SettingsRepository] (docs/specs/spec-settings.md). On init it
 * loads (provisioning a default row on first access); each update intent applies the
 * change to the current settings, persists it, and reflects the persisted row back
 * into [uiState] so the screen always renders what storage holds.
 *
 * Update intents are no-ops until the settings have loaded, so the screen can bind
 * them unconditionally. All Supabase access stays in the repository; Composables hold
 * no business logic and observe [uiState] only.
 */
class SettingsViewModel(
    private val repository: SettingsRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow<SettingsUiState>(SettingsUiState.Loading)
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    /** (Re)load the settings, showing the spinner; provisions defaults on first access. */
    fun load() {
        _uiState.value = SettingsUiState.Loading
        viewModelScope.launch {
            runCatching { repository.getSettings() }
                .onSuccess { settings -> _uiState.value = stateFor(settings) }
                .onFailure { error -> _uiState.value = SettingsUiState.Error(messageFor(error)) }
        }
    }

    /** Set the display/entry weight unit (storage stays canonical kg). */
    fun setWeightUnit(unit: WeightUnit) = persist { it.copy(weightUnit = unit) }

    /** Set the theme choice (resolves to the Recomp dark palette this phase). */
    fun setTheme(theme: String) = persist { it.copy(theme = theme) }

    /** Set the rest-timer default (seconds) for the given [kind]. */
    fun setRestSeconds(
        kind: RestDefaultKind,
        seconds: Int,
    ) = persist { it.withRestSeconds(kind, seconds) }

    /** Set the editable profile display name. */
    fun setDisplayName(displayName: String) = persist { it.copy(displayName = displayName) }

    /**
     * Apply [change] to the loaded settings, persist the result, and reflect the
     * stored row back into state. A no-op until the settings have loaded.
     */
    private fun persist(change: (UserSettings) -> UserSettings) {
        val current = (_uiState.value as? SettingsUiState.Content)?.settings ?: return
        viewModelScope.launch {
            runCatching { repository.updateSettings(change(current)) }
                .onSuccess { settings -> _uiState.value = stateFor(settings) }
                .onFailure { error -> _uiState.value = SettingsUiState.Error(messageFor(error)) }
        }
    }

    private fun stateFor(settings: UserSettings?): SettingsUiState =
        settings?.let { SettingsUiState.Content(it) } ?: SettingsUiState.Error(NO_SESSION_ERROR)

    private fun messageFor(error: Throwable): String = error.message?.takeIf { it.isNotBlank() } ?: GENERIC_ERROR

    private companion object {
        const val NO_SESSION_ERROR = "You are signed out. Sign in again to manage your settings."
        const val GENERIC_ERROR = "Could not load your settings. Check your connection and try again."
    }
}

/** Returns a copy with the [kind]'s rest default set to [seconds], leaving the others unchanged. */
private fun UserSettings.withRestSeconds(
    kind: RestDefaultKind,
    seconds: Int,
): UserSettings =
    when (kind) {
        RestDefaultKind.COMPOUND -> copy(restCompoundSeconds = seconds)
        RestDefaultKind.ISOLATION -> copy(restIsolationSeconds = seconds)
        RestDefaultKind.SUPERSET -> copy(restSupersetSeconds = seconds)
        RestDefaultKind.CIRCUIT -> copy(restCircuitSeconds = seconds)
    }
