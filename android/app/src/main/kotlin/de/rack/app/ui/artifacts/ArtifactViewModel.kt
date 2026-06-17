package de.rack.app.ui.artifacts

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.rack.app.data.ArtifactRepository
import de.rack.app.domain.Artifact
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Artifacts-list UI state observed by the screen: loading, content (possibly empty), or error. */
sealed interface ArtifactUiState {
    data object Loading : ArtifactUiState

    data class Content(val artifacts: List<Artifact>) : ArtifactUiState

    data class Error(val message: String) : ArtifactUiState
}

/**
 * The artifacts-screen actions, bundled so the screen takes one parameter for
 * refresh, retry, opening an artifact, and navigating back instead of separate lambdas.
 */
@Immutable
data class ArtifactActions(
    val onRefresh: () -> Unit,
    val onRetry: () -> Unit,
    val onOpen: (String) -> Unit,
    val onBack: () -> Unit,
)

/**
 * Reads the signed-in user's artifacts through the [ArtifactRepository] (all under
 * RLS — no `user_id` is ever sent) and exposes them as [StateFlow], newest first.
 * [isRefreshing] drives the pull-to-refresh indicator while the list re-fetches in
 * place without dropping to the loading spinner. No Supabase access happens in
 * Composables; the screen observes the flows and emits events only.
 */
class ArtifactViewModel(
    private val repository: ArtifactRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow<ArtifactUiState>(ArtifactUiState.Loading)
    val uiState: StateFlow<ArtifactUiState> = _uiState.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    init {
        load()
    }

    /** (Re)load the artifact list, showing the full-screen spinner. */
    fun load() {
        _uiState.value = ArtifactUiState.Loading
        viewModelScope.launch { fetch() }
    }

    /** Re-fetch in place for pull-to-refresh, keeping the current list visible. */
    fun refresh() {
        if (_isRefreshing.value) return
        _isRefreshing.update { true }
        viewModelScope.launch {
            fetch()
            _isRefreshing.update { false }
        }
    }

    private suspend fun fetch() {
        runCatching { repository.getArtifacts() }
            .onSuccess { artifacts -> _uiState.value = ArtifactUiState.Content(artifacts) }
            .onFailure { error -> _uiState.value = ArtifactUiState.Error(messageFor(error)) }
    }

    private fun messageFor(error: Throwable): String = error.message?.takeIf { it.isNotBlank() } ?: GENERIC_ERROR

    private companion object {
        const val GENERIC_ERROR = "Could not load your artifacts. Check your connection and try again."
    }
}
