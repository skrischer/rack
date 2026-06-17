package de.rack.app.ui.exercise

import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.rack.app.data.ExerciseRepository
import de.rack.app.domain.ExerciseDetail
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * What the detail screen shows for an exercise's image slot: either a decoded
 * licensed image or a deterministic placeholder keyed on primary muscle → category →
 * `generic` (spec-exercise-detail.md). Resolved in the ViewModel so the Composable
 * stays free of decoding and fallback logic.
 */
sealed interface ExerciseImage {
    /** A decoded wger CC-BY-SA image to render in an image view. */
    data class Loaded(val image: ImageBitmap) : ExerciseImage

    /** No licensed image (or a failed decode): show the [key]'ed placeholder. */
    data class Placeholder(val key: String) : ExerciseImage
}

/** Detail-screen UI state: loading, the resolved content, or an unrecoverable error. */
sealed interface ExerciseDetailUiState {
    data object Loading : ExerciseDetailUiState

    data class Content(val detail: ExerciseDetail, val image: ExerciseImage) : ExerciseDetailUiState

    data class Error(val message: String) : ExerciseDetailUiState
}

/**
 * Resolves a single catalog exercise's execution detail for the detail screen. It
 * reads the public-read [ExerciseDetail] by id through the [ExerciseRepository] (anon
 * key only — the catalog carries no owner RLS), then resolves the image slot: when
 * the row has a licensed image its bytes are downloaded and decoded into an
 * [ExerciseImage.Loaded]; otherwise (or on a failed download/decode) it falls back to
 * a deterministic [ExerciseImage.Placeholder]. Bitmap decoding is injected via
 * [decodeImage] so the ViewModel stays unit-testable and free of Android imaging.
 * No Supabase access and no rendering happen here — only state. See
 * docs/specs/spec-exercise-detail.md.
 */
class ExerciseDetailViewModel(
    private val repository: ExerciseRepository,
    private val exerciseId: String,
    private val decodeImage: (ByteArray) -> ImageBitmap?,
) : ViewModel() {
    private val _uiState = MutableStateFlow<ExerciseDetailUiState>(ExerciseDetailUiState.Loading)
    val uiState: StateFlow<ExerciseDetailUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    /** (Re)load the exercise detail and its image; retried from the error pane. */
    fun load() {
        _uiState.value = ExerciseDetailUiState.Loading
        viewModelScope.launch {
            runCatching { resolve() }
                .onSuccess { state -> _uiState.value = state }
                .onFailure { error -> _uiState.value = errorState(error.message) }
        }
    }

    private suspend fun resolve(): ExerciseDetailUiState {
        val detail = repository.getExerciseDetail(exerciseId) ?: return errorState(MISSING)
        return ExerciseDetailUiState.Content(detail = detail, image = resolveImage(detail))
    }

    /**
     * Download and decode the licensed image, falling back to the deterministic
     * placeholder when the row has no image URL or the fetch/decode fails — so the
     * screen never shows a blank image slot.
     */
    private suspend fun resolveImage(detail: ExerciseDetail): ExerciseImage {
        val url = detail.imageUrl?.takeIf { it.isNotBlank() } ?: return placeholder(detail)
        return runCatching { repository.downloadImageBytes(url) }
            .mapCatching { bytes -> decodeImage(bytes) }
            .getOrNull()
            ?.let(ExerciseImage::Loaded)
            ?: placeholder(detail)
    }

    private fun placeholder(detail: ExerciseDetail): ExerciseImage = ExerciseImage.Placeholder(placeholderKey(detail))

    private fun errorState(message: String?): ExerciseDetailUiState.Error =
        ExerciseDetailUiState.Error(message?.takeIf { it.isNotBlank() } ?: GENERIC_ERROR)

    private companion object {
        const val MISSING = "This exercise is no longer available."
        const val GENERIC_ERROR = "Could not load this exercise. Check your connection and try again."
    }
}
