package de.rack.app.di

import de.rack.app.data.SupabaseClientProvider
import io.github.jan.supabase.SupabaseClient

/**
 * Tiny manual dependency container (no Hilt/Koin — the object graph is small).
 *
 * Holds the single shared [SupabaseClient]; repositories and ViewModels added in
 * later issues (#21/#22/#23) are wired here via constructor injection.
 */
class AppContainer {
    val supabaseClient: SupabaseClient by lazy { SupabaseClientProvider.create() }
}
