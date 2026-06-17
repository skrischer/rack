package de.rack.app.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.flow.StateFlow

/**
 * The single Supabase Auth access point. Wraps `client.auth` so the rest of the
 * app never touches the SDK directly — ViewModels consume this, Composables never
 * do. The shared [SupabaseClient] persists the session itself (Settings-backed
 * session manager on Android), so [sessionStatus] is restored across restarts.
 */
class AuthRepository(
    private val client: SupabaseClient,
) {
    /** The live session status used for signed-in vs signed-out routing. */
    val sessionStatus: StateFlow<SessionStatus> = client.auth.sessionStatus

    /** Sign in with email/password; throws on invalid credentials. */
    suspend fun signIn(
        email: String,
        password: String,
    ) {
        client.auth.signInWith(Email) {
            this.email = email
            this.password = password
        }
    }

    /** Clear the persisted session and return to the signed-out state. */
    suspend fun signOut() {
        client.auth.signOut()
    }
}
