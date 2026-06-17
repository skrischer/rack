package de.rack.app.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import de.rack.app.ui.theme.RecompTheme

/**
 * Signed-out login screen. Purely presentational: it observes [state] and emits
 * sign-in / dismiss-error events upward — no Supabase access and no business logic.
 * Routing to the plan view happens in the nav host once the session is authenticated.
 */
@Composable
fun LoginScreen(
    state: LoginUiState,
    onSignIn: (email: String, password: String) -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = RecompTheme.colors
    val type = RecompTheme.typography
    val spacing = RecompTheme.spacing
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    val canSubmit = email.isNotBlank() && password.isNotBlank() && !state.isSubmitting

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(colors.bg)
                .padding(horizontal = spacing.gutter)
                .padding(top = LOGIN_TOP_PADDING),
        verticalArrangement = Arrangement.spacedBy(spacing.lg),
    ) {
        Text(text = "RACK", style = type.kicker, color = colors.volt)
        Text(text = "SIGN IN", style = type.hero, color = colors.txt)

        AuthField(
            kind = FieldKind.EMAIL,
            value = email,
            onValueChange = {
                email = it
                if (state.errorMessage != null) onDismissError()
            },
            enabled = !state.isSubmitting,
        )
        AuthField(
            kind = FieldKind.PASSWORD,
            value = password,
            onValueChange = {
                password = it
                if (state.errorMessage != null) onDismissError()
            },
            enabled = !state.isSubmitting,
            onImeAction = { if (canSubmit) onSignIn(email, password) },
        )

        state.errorMessage?.let { message ->
            Text(text = message, style = type.label, color = colors.legs)
        }

        SignInButton(
            isSubmitting = state.isSubmitting,
            enabled = canSubmit,
            onClick = { onSignIn(email, password) },
        )
    }
}

@Composable
private fun SignInButton(
    isSubmitting: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = RecompTheme.colors
    val type = RecompTheme.typography
    val fill = if (enabled || isSubmitting) colors.volt else colors.voltDim
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(BUTTON_HEIGHT)
                .background(fill, RecompTheme.shapes.md)
                .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        if (isSubmitting) {
            CircularProgressIndicator(
                color = colors.bg,
                strokeWidth = 2.dp,
                modifier = Modifier.size(SPINNER_SIZE),
            )
        } else {
            Text(text = "SIGN IN", style = type.label, color = colors.bg)
        }
    }
}

/** The two login fields, each fixing its keyboard, IME action, and masking. */
private enum class FieldKind(
    val label: String,
    val keyboardType: KeyboardType,
    val imeAction: ImeAction,
    val masked: Boolean,
) {
    EMAIL("EMAIL", KeyboardType.Email, ImeAction.Next, masked = false),
    PASSWORD("PASSWORD", KeyboardType.Password, ImeAction.Done, masked = true),
}

@Composable
private fun AuthField(
    kind: FieldKind,
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    onImeAction: () -> Unit = {},
) {
    val colors = RecompTheme.colors
    val type = RecompTheme.typography
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        singleLine = true,
        label = { Text(text = kind.label, style = type.label) },
        textStyle = type.intro,
        shape = RecompTheme.shapes.sm,
        visualTransformation = if (kind.masked) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = kind.keyboardType, imeAction = kind.imeAction),
        keyboardActions = KeyboardActions(onDone = { onImeAction() }),
        colors =
            TextFieldDefaults.colors(
                focusedContainerColor = colors.panel,
                unfocusedContainerColor = colors.panel,
                disabledContainerColor = colors.panel,
                focusedTextColor = colors.txt,
                unfocusedTextColor = colors.txt,
                cursorColor = colors.volt,
                focusedIndicatorColor = colors.volt,
                unfocusedIndicatorColor = colors.line,
                focusedLabelColor = colors.volt,
                unfocusedLabelColor = colors.dim,
            ),
        modifier = Modifier.fillMaxWidth(),
    )
}

private val LOGIN_TOP_PADDING = 96.dp
private val BUTTON_HEIGHT = 52.dp
private val SPINNER_SIZE = 20.dp
