package de.rack.app.data

import de.rack.app.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.storage.Storage

/**
 * Builds the single [SupabaseClient] the app shares app-wide.
 *
 * The client uses the anon key only (never a service-role key); credentials come
 * from [BuildConfig] sourced from android/local.properties. Auth, Postgrest,
 * Realtime, and Storage are installed; Realtime and Storage stay unused until
 * Phases 4 and 6. All Supabase access lives behind the repository layer — never
 * call this from a Composable.
 */
object SupabaseClientProvider {
    fun create(): SupabaseClient =
        createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_ANON_KEY,
        ) {
            install(Auth)
            install(Postgrest)
            install(Realtime)
            install(Storage)
        }
}
