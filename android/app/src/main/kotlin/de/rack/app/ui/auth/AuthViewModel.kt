package de.rack.app.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.rack.app.data.AuthRepository
import io.github.jan.supabase.auth.exception.AuthErrorCode
import io.github.jan.supabase.auth.exception.AuthRestException
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Top-level route derived from the persisted session, used for signed-in routing. */
enum class AuthRoute { Loading, SignedOut, SignedIn }

/** Login form state observed by the login screen; it emits events, never logic. */
data class LoginUiState(
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
)

/**
 * Exposes auth state as [StateFlow] and handles sign-in/sign-out events. The
 * [route] is mapped from the repository's session status so the nav host can route
 * between login and the plan view; [loginState] carries submission/error for the
 * login form. No Supabase access happens here beyond the repository calls.
 */
class AuthViewModel(
    private val authRepository: AuthRepository,
) : ViewModel() {
    val route: StateFlow<AuthRoute> =
        authRepository.sessionStatus
            .map(::toRoute)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), AuthRoute.Loading)

    private val _loginState = MutableStateFlow(LoginUiState())
    val loginState: StateFlow<LoginUiState> = _loginState.asStateFlow()

    fun signIn(
        email: String,
        password: String,
    ) {
        if (_loginState.value.isSubmitting) return
        _loginState.update { it.copy(isSubmitting = true, errorMessage = null) }
        viewModelScope.launch {
            runCatching { authRepository.signIn(email.trim(), password) }
                .onSuccess { _loginState.update { it.copy(isSubmitting = false) } }
                .onFailure { error ->
                    _loginState.update { it.copy(isSubmitting = false, errorMessage = messageFor(error)) }
                }
        }
    }

    fun dismissError() = _loginState.update { it.copy(errorMessage = null) }

    fun signOut() {
        viewModelScope.launch { authRepository.signOut() }
    }

    private fun toRoute(status: SessionStatus): AuthRoute =
        when (status) {
            is SessionStatus.Authenticated -> AuthRoute.SignedIn
            is SessionStatus.Initializing -> AuthRoute.Loading
            is SessionStatus.NotAuthenticated -> AuthRoute.SignedOut
            is SessionStatus.RefreshFailure -> AuthRoute.SignedOut
        }

    private fun messageFor(error: Throwable): String =
        when {
            error is AuthRestException && error.errorCode == AuthErrorCode.InvalidCredentials ->
                "Invalid email or password."
            error is AuthRestException -> error.message ?: GENERIC_ERROR
            else -> error.message?.takeIf { it.isNotBlank() } ?: GENERIC_ERROR
        }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val GENERIC_ERROR = "Sign-in failed. Check your connection and try again."
    }
}
