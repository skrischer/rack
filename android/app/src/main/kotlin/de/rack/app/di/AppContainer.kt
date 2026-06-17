package de.rack.app.di

import android.content.Context
import de.rack.app.data.ApiKeyRepository
import de.rack.app.data.AppLifecycleObserver
import de.rack.app.data.ArtifactRepository
import de.rack.app.data.AuthRepository
import de.rack.app.data.ConnectivityObserver
import de.rack.app.data.ExerciseRepository
import de.rack.app.data.LoggingRepository
import de.rack.app.data.RealtimeRepository
import de.rack.app.data.SupabaseClientProvider
import de.rack.app.data.TimerController
import de.rack.app.data.TrainingRepository
import de.rack.app.data.local.RackDatabase
import io.github.jan.supabase.SupabaseClient

/**
 * Tiny manual dependency container (no Hilt/Koin — the object graph is small).
 *
 * Holds the single shared [SupabaseClient], the Room cache, and the repository
 * layer built over them via constructor injection; ViewModels consume these via
 * the [AppContainer] exposed through the composition.
 */
class AppContainer(
    context: Context,
) {
    val appContext: Context = context.applicationContext

    val supabaseClient: SupabaseClient by lazy { SupabaseClientProvider.create() }

    private val database: RackDatabase by lazy { RackDatabase.create(appContext) }

    val connectivityObserver: ConnectivityObserver by lazy { ConnectivityObserver(appContext) }

    // Eager: registers on ProcessLifecycleOwner at app start (main thread) so the
    // foreground signal is live before any screen subscribes to Realtime.
    val appLifecycleObserver: AppLifecycleObserver = AppLifecycleObserver()

    val authRepository: AuthRepository by lazy { AuthRepository(supabaseClient) }

    val apiKeyRepository: ApiKeyRepository by lazy { ApiKeyRepository(supabaseClient) }

    val artifactRepository: ArtifactRepository by lazy { ArtifactRepository(supabaseClient) }

    val exerciseRepository: ExerciseRepository by lazy { ExerciseRepository(supabaseClient) }

    val trainingRepository: TrainingRepository by lazy { TrainingRepository(supabaseClient) }

    val realtimeRepository: RealtimeRepository by lazy { RealtimeRepository(supabaseClient) }

    // Process-wide host of the Phase-8 rest + session timers; shared by the
    // foreground TimerService and the TimerViewModel so both read one source of truth.
    val timerController: TimerController by lazy { TimerController() }

    val loggingRepository: LoggingRepository by lazy {
        LoggingRepository(trainingRepository, database.pendingLogDao())
    }
}
