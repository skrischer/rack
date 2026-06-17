package de.rack.app.ui.keys

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.rack.app.data.ApiKeyRepository
import de.rack.app.domain.ApiKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Key-list UI state observed by the screen: loading, content (possibly empty), or error. */
sealed interface ApiKeyListState {
    data object Loading : ApiKeyListState

    data class Content(val keys: List<ApiKey>) : ApiKeyListState

    data class Error(val message: String) : ApiKeyListState
}

/**
 * Drives the key-management screen: lists the signed-in user's keys, mints a new
 * one, and revokes an existing one — all through [ApiKeyRepository] over the
 * JWT-authenticated MCP admin endpoints (no Supabase Postgrest, no service-role).
 *
 * The plaintext of a freshly minted key is held only in [revealedKey], a transient
 * in-memory state shown exactly once; [clearRevealedKey] discards it irrecoverably
 * (the server cannot return it again) and must be called when the reveal surface is
 * left. No business logic lives in Composables; the screen observes the flows and
 * emits events only.
 */
class ApiKeyViewModel(
    private val repository: ApiKeyRepository,
) : ViewModel() {
    private val _listState = MutableStateFlow<ApiKeyListState>(ApiKeyListState.Loading)
    val listState: StateFlow<ApiKeyListState> = _listState.asStateFlow()

    private val _revealedKey = MutableStateFlow<String?>(null)
    val revealedKey: StateFlow<String?> = _revealedKey.asStateFlow()

    private val _isCreating = MutableStateFlow(false)
    val isCreating: StateFlow<Boolean> = _isCreating.asStateFlow()

    init {
        load()
    }

    /** (Re)load the user's keys into [listState]. */
    fun load() {
        _listState.value = ApiKeyListState.Loading
        viewModelScope.launch {
            runCatching { repository.list() }
                .onSuccess { keys -> _listState.value = ApiKeyListState.Content(keys) }
                .onFailure { error -> _listState.value = ApiKeyListState.Error(messageFor(error)) }
        }
    }

    /** Mint a named key; on success reveal its plaintext once and refresh the list. */
    fun create(name: String) {
        if (_isCreating.value || name.isBlank()) return
        _isCreating.update { true }
        viewModelScope.launch {
            runCatching { repository.create(name) }
                .onSuccess { created ->
                    _revealedKey.value = created.plaintext
                    load()
                }
                .onFailure { error -> _listState.value = ApiKeyListState.Error(messageFor(error)) }
            _isCreating.update { false }
        }
    }

    /** Revoke a key, then refresh the list to reflect its revoked state. */
    fun revoke(keyId: String) {
        viewModelScope.launch {
            runCatching { repository.revoke(keyId) }
                .onSuccess { load() }
                .onFailure { error -> _listState.value = ApiKeyListState.Error(messageFor(error)) }
        }
    }

    /** Discard the one-time plaintext; call when leaving the reveal surface. */
    fun clearRevealedKey() {
        _revealedKey.value = null
    }

    private fun messageFor(error: Throwable): String = error.message?.takeIf { it.isNotBlank() } ?: GENERIC_ERROR

    private companion object {
        const val GENERIC_ERROR = "Could not reach the rack-MCP. Check your connection and try again."
    }
}
