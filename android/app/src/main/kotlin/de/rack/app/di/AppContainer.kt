package de.rack.app.di

import android.content.Context
import de.rack.app.data.AuthRepository
import de.rack.app.data.ConnectivityObserver
import de.rack.app.data.LoggingRepository
import de.rack.app.data.SupabaseClientProvider
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
    private val appContext = context.applicationContext

    val supabaseClient: SupabaseClient by lazy { SupabaseClientProvider.create() }

    private val database: RackDatabase by lazy { RackDatabase.create(appContext) }

    val connectivityObserver: ConnectivityObserver by lazy { ConnectivityObserver(appContext) }

    val authRepository: AuthRepository by lazy { AuthRepository(supabaseClient) }

    val trainingRepository: TrainingRepository by lazy { TrainingRepository(supabaseClient) }

    val loggingRepository: LoggingRepository by lazy {
        LoggingRepository(trainingRepository, database.pendingLogDao())
    }
}
