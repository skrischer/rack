package de.rack.app.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import de.rack.app.ui.theme.RecompLoading
import de.rack.app.ui.theme.RecompPrimaryButton
import de.rack.app.ui.theme.RecompTheme
import de.rack.app.ui.theme.SplineSansMonoFamily

/**
 * Signed-out login screen. Purely
 * presentational: it observes [state] and emits sign-in / dismiss-error events upward —
 * no Supabase access and no business logic. Routing to the plan view happens in the nav
 * host once the session is authenticated.
 */
@Composable
fun LoginScreen(
    state: LoginUiState,
    onSignIn: (email: String, password: String) -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = RecompTheme.colors
    val spacing = RecompTheme.spacing

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(colors.bg)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.gutter)
                .padding(top = LOGIN_TOP_PADDING, bottom = spacing.xxl),
    ) {
        LoginHeader()
        Spacer(modifier = Modifier.height(spacing.huge))
        LoginCard(state = state, onSignIn = onSignIn, onDismissError = onDismissError)
        Spacer(modifier = Modifier.height(spacing.xxl))
        Text(
            text = "rack-mcp · v1.0.0",
            style = RecompTheme.typography.caption,
            color = colors.dim,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Kicker, two-tone hero, and the agent-authored subtitle from the design's intro block. */
@Composable
private fun LoginHeader() {
    val colors = RecompTheme.colors
    val type = RecompTheme.typography
    val spacing = RecompTheme.spacing
    Text(text = "RACK", style = type.kicker, color = colors.volt)
    Spacer(modifier = Modifier.height(spacing.sm))
    Text(
        text =
            buildAnnotatedString {
                append("STÄRKER\n")
                withStyle(SpanStyle(color = colors.dim)) { append("WERDEN") }
            },
        style = type.hero,
        color = colors.txt,
    )
    Spacer(modifier = Modifier.height(spacing.md))
    Text(
        text = "Dein Trainingsplan, von deinem eigenen Agenten geschrieben — live in der App.",
        style = type.intro,
        color = colors.dim,
    )
}

/** The panel card (`.card`): two stacked fields over a foot holding the CTA and error line. */
@Composable
private fun LoginCard(
    state: LoginUiState,
    onSignIn: (email: String, password: String) -> Unit,
    onDismissError: () -> Unit,
) {
    val colors = RecompTheme.colors
    val spacing = RecompTheme.spacing
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    val canSubmit = email.isNotBlank() && password.isNotBlank() && !state.isSubmitting
    val onSubmit = { if (canSubmit) onSignIn(email, password) }
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(colors.panel, RecompTheme.shapes.xl)
                .border(spacing.border, colors.line, RecompTheme.shapes.xl)
                .padding(spacing.cardInsetH),
        verticalArrangement = Arrangement.spacedBy(spacing.lg),
    ) {
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
            onImeAction = onSubmit,
        )
        CardFoot(state = state, canSubmit = canSubmit, onSubmit = onSubmit)
    }
}

/** The card foot (`.card-foot`): the submit CTA (or its loading state) over an inline error. */
@Composable
private fun CardFoot(
    state: LoginUiState,
    canSubmit: Boolean,
    onSubmit: () -> Unit,
) {
    if (state.isSubmitting) {
        RecompLoading()
        return
    }
    RecompPrimaryButton(text = "Anmelden", onClick = onSubmit, enabled = canSubmit, fillMaxWidth = true)
    state.errorMessage?.let { message ->
        Text(
            text = message,
            style = RecompTheme.typography.caption,
            color = RecompTheme.colors.dim,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** The two login fields, each fixing its keyboard, IME action, masking, and German cap. */
private enum class FieldKind(
    val label: String,
    val keyboardType: KeyboardType,
    val imeAction: ImeAction,
    val masked: Boolean,
) {
    EMAIL("Mail", KeyboardType.Email, ImeAction.Next, masked = false),
    PASSWORD("Passwort", KeyboardType.Password, ImeAction.Done, masked = true),
}

/**
 * A login input mirroring the kit `.in` + `.cap` pairing: a mono uppercase caption over a
 * full-width, left-aligned mono field on the `bg` surface whose border turns volt on focus.
 */
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
    val spacing = RecompTheme.spacing
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val borderColor = if (focused) colors.volt else colors.line
    Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
        Text(text = kind.label.uppercase(), style = type.caption, color = colors.dim)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = true,
            textStyle = type.intro.copy(fontFamily = SplineSansMonoFamily, color = colors.txt),
            cursorBrush = SolidColor(colors.volt),
            visualTransformation = if (kind.masked) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = kind.keyboardType, imeAction = kind.imeAction),
            keyboardActions = KeyboardActions(onDone = { onImeAction() }),
            interactionSource = interaction,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(colors.bg, RecompTheme.shapes.sm)
                    .border(spacing.border, borderColor, RecompTheme.shapes.sm)
                    .padding(horizontal = spacing.md, vertical = FIELD_PADDING_V),
        )
    }
}

private val LOGIN_TOP_PADDING = 64.dp
private val FIELD_PADDING_V = 11.dp
