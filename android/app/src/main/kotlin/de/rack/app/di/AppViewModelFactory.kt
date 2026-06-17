package de.rack.app.di

import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import de.rack.app.timer.TimerService
import de.rack.app.ui.artifacts.ArtifactViewModel
import de.rack.app.ui.artifacts.ArtifactViewerViewModel
import de.rack.app.ui.auth.AuthViewModel
import de.rack.app.ui.exercise.ExerciseDetailViewModel
import de.rack.app.ui.keys.ApiKeyViewModel
import de.rack.app.ui.logging.LoggingViewModel
import de.rack.app.ui.plan.PlanViewModel
import de.rack.app.ui.timer.TimerViewModel

/**
 * Manual ViewModel factory wiring the container's repositories into ViewModels —
 * the constructor-injection equivalent of a DI graph without Hilt/Koin. New
 * ViewModels register an [initializer] here.
 */
fun appViewModelFactory(container: AppContainer): ViewModelProvider.Factory =
    viewModelFactory {
        initializer { AuthViewModel(container.authRepository) }
        initializer { ApiKeyViewModel(container.apiKeyRepository) }
        initializer { ArtifactViewModel(container.artifactRepository) }
        initializer {
            PlanViewModel(
                container.trainingRepository,
                container.realtimeRepository,
                container.appLifecycleObserver,
            )
        }
        initializer {
            LoggingViewModel(
                container.trainingRepository,
                container.loggingRepository,
                container.realtimeRepository,
                container.connectivityObserver,
                container.appLifecycleObserver,
            )
        }
        initializer {
            TimerViewModel(
                controller = container.timerController,
                onSessionStart = { TimerService.start(container.appContext) },
                onSessionStop = { TimerService.stop(container.appContext) },
            )
        }
    }

/**
 * Factory for the per-artifact viewer ViewModel, which needs a runtime [artifactId]
 * plus a PNG [decodePng] step (Android bitmap decoding kept out of the ViewModel so
 * it stays unit-testable). Separate from [appViewModelFactory] because its inputs
 * are route-scoped, not container-scoped.
 */
fun artifactViewerViewModelFactory(
    container: AppContainer,
    artifactId: String,
    decodePng: (ByteArray) -> ImageBitmap?,
): ViewModelProvider.Factory =
    viewModelFactory {
        initializer {
            ArtifactViewerViewModel(
                repository = container.artifactRepository,
                artifactId = artifactId,
                decodePng = decodePng,
            )
        }
    }

/**
 * Factory for the per-exercise detail ViewModel, which needs a runtime [exerciseId]
 * plus an image [decodeImage] step (Android bitmap decoding kept out of the ViewModel
 * so it stays unit-testable). Separate from [appViewModelFactory] because its inputs
 * are route-scoped, not container-scoped.
 */
fun exerciseDetailViewModelFactory(
    container: AppContainer,
    exerciseId: String,
    decodeImage: (ByteArray) -> ImageBitmap?,
): ViewModelProvider.Factory =
    viewModelFactory {
        initializer {
            ExerciseDetailViewModel(
                repository = container.exerciseRepository,
                exerciseId = exerciseId,
                decodeImage = decodeImage,
            )
        }
    }
