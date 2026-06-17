package de.rack.app.di

import de.rack.app.data.AuthRepository
import de.rack.app.data.SupabaseClientProvider
import de.rack.app.data.TrainingRepository
import io.github.jan.supabase.SupabaseClient

/**
 * Tiny manual dependency container (no Hilt/Koin — the object graph is small).
 *
 * Holds the single shared [SupabaseClient] and the repository layer built over
 * it via constructor injection; ViewModels consume these via the [AppContainer]
 * exposed through the composition.
 */
class AppContainer {
    val supabaseClient: SupabaseClient by lazy { SupabaseClientProvider.create() }

    val authRepository: AuthRepository by lazy { AuthRepository(supabaseClient) }

    val trainingRepository: TrainingRepository by lazy { TrainingRepository(supabaseClient) }
}
