package de.rack.app.ui.artifacts

import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.rack.app.data.ArtifactRepository
import de.rack.app.domain.Artifact
import de.rack.app.ui.artifacts.ArtifactViewerContent.ImageContent
import de.rack.app.ui.artifacts.ArtifactViewerContent.WebContent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** What the viewer should render once an artifact's signed URL (and, for PNG, its bytes) resolve. */
sealed interface ArtifactViewerContent {
    /** HTML/SVG: the signed URL is loaded into the sandboxed WebView. */
    data class WebContent(val signedUrl: String, val isSvg: Boolean) : ArtifactViewerContent

    /** PNG: bytes already decoded into an image to show in an image view. */
    data class ImageContent(val image: ImageBitmap) : ArtifactViewerContent
}

/** Viewer UI state: loading the signed URL/bytes, ready content, or an unrenderable fallback. */
sealed interface ArtifactViewerUiState {
    data object Loading : ArtifactViewerUiState

    data class Ready(val title: String, val content: ArtifactViewerContent) : ArtifactViewerUiState

    data class Error(val title: String, val message: String) : ArtifactViewerUiState
}

/**
 * Resolves a single artifact's renderable content for the viewer screen. It reads
 * the artifact's metadata by id (RLS-scoped), mints a short-lived signed URL from
 * the private bucket via the [ArtifactRepository], and chooses the render strategy
 * from the MIME type: `text/html`/`image/svg+xml` load into a sandboxed WebView,
 * `image/png` is downloaded and decoded into an image. An unsupported type or any
 * fetch/decode failure becomes an [ArtifactViewerUiState.Error] so the screen shows
 * a fallback. No Supabase access and no rendering happen here — only state.
 */
class ArtifactViewerViewModel(
    private val repository: ArtifactRepository,
    private val artifactId: String,
    private val decodePng: (ByteArray) -> ImageBitmap?,
) : ViewModel() {
    private val _uiState = MutableStateFlow<ArtifactViewerUiState>(ArtifactViewerUiState.Loading)
    val uiState: StateFlow<ArtifactViewerUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    /** (Re)resolve the artifact's signed content; retried from the fallback. */
    fun load() {
        _uiState.value = ArtifactViewerUiState.Loading
        viewModelScope.launch {
            runCatching { resolve() }
                .onSuccess { state -> _uiState.value = state }
                .onFailure { error -> _uiState.value = errorState(error.message) }
        }
    }

    private suspend fun resolve(): ArtifactViewerUiState {
        val artifact = repository.getArtifact(artifactId) ?: return errorState(MISSING)
        val storagePath = artifact.storagePath?.takeIf { it.isNotBlank() }
        return when {
            storagePath == null -> errorState(UNRENDERABLE, artifact)
            artifact.type == TYPE_HTML -> webState(artifact, storagePath, isSvg = false)
            artifact.type == TYPE_SVG -> webState(artifact, storagePath, isSvg = true)
            artifact.type == TYPE_PNG -> imageState(artifact, storagePath)
            else -> errorState(UNRENDERABLE, artifact)
        }
    }

    private suspend fun webState(
        artifact: Artifact,
        storagePath: String,
        isSvg: Boolean,
    ): ArtifactViewerUiState =
        ArtifactViewerUiState.Ready(
            title = titleFor(artifact),
            content = WebContent(signedUrl = repository.signedUrlFor(storagePath), isSvg = isSvg),
        )

    private suspend fun imageState(
        artifact: Artifact,
        storagePath: String,
    ): ArtifactViewerUiState {
        val signedUrl = repository.signedUrlFor(storagePath)
        val image = decodePng(repository.downloadBytes(signedUrl)) ?: return errorState(UNRENDERABLE, artifact)
        return ArtifactViewerUiState.Ready(title = titleFor(artifact), content = ImageContent(image))
    }

    private fun errorState(
        message: String?,
        artifact: Artifact? = null,
    ): ArtifactViewerUiState.Error =
        ArtifactViewerUiState.Error(
            title = artifact?.let(::titleFor) ?: DEFAULT_TITLE,
            message = message?.takeIf { it.isNotBlank() } ?: GENERIC_ERROR,
        )

    private fun titleFor(artifact: Artifact): String = artifact.name?.takeIf { it.isNotBlank() } ?: DEFAULT_TITLE

    private companion object {
        const val TYPE_HTML = "text/html"
        const val TYPE_SVG = "image/svg+xml"
        const val TYPE_PNG = "image/png"
        const val DEFAULT_TITLE = "Untitled artifact"
        const val MISSING = "This artifact is no longer available."
        const val UNRENDERABLE = "This artifact type cannot be displayed."
        const val GENERIC_ERROR = "Could not load this artifact. Check your connection and try again."
    }
}
