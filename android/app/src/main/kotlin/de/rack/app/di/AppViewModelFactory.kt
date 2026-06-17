package de.rack.app.di

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import de.rack.app.ui.auth.AuthViewModel
import de.rack.app.ui.logging.LoggingViewModel
import de.rack.app.ui.plan.PlanViewModel

/**
 * Manual ViewModel factory wiring the container's repositories into ViewModels —
 * the constructor-injection equivalent of a DI graph without Hilt/Koin. New
 * ViewModels register an [initializer] here.
 */
fun appViewModelFactory(container: AppContainer): ViewModelProvider.Factory =
    viewModelFactory {
        initializer { AuthViewModel(container.authRepository) }
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
    }
