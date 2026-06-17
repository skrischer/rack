package de.rack.app.ui.keys

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import de.rack.app.domain.ApiKey
import de.rack.app.ui.theme.RecompTheme

/**
 * Recomp key-management + onboarding screen: lists the signed-in user's rack-MCP
 * API keys, mints a new named one, walks a first-time user through connecting an
 * MCP client, and revokes existing keys. Purely presentational — it renders
 * [state] and emits create / revoke / retry / back events upward; the onboarding
 * overlay is shown from [ApiKeyReveal] while [ApiKeyState.revealedKey] is non-null.
 * No Supabase access and no business logic live here.
 */
@Composable
fun ApiKeyScreen(
    state: ApiKeyState,
    actions: ApiKeyActions,
    modifier: Modifier = Modifier,
) {
    val colors = RecompTheme.colors
    Box(modifier = modifier.fillMaxSize().background(colors.bg)) {
        Column(modifier = Modifier.fillMaxSize()) {
            KeysTopBar(onBack = actions.onBack)
            Box(modifier = Modifier.weight(1f)) {
                when (val list = state.list) {
                    is ApiKeyListState.Loading -> CenterSpinner()
                    is ApiKeyListState.Error -> ErrorPane(message = list.message, onRetry = actions.onRetry)
                    is ApiKeyListState.Content ->
                        KeyListPane(
                            keys = list.keys,
                            isCreating = state.isCreating,
                            endpointUrl = state.endpointUrl,
                            onCreate = actions.onCreate,
                            onRevoke = actions.onRevoke,
                        )
                }
            }
        }
        if (state.revealedKey != null) {
            ApiKeyReveal(
                plaintext = state.revealedKey,
                endpointUrl = state.endpointUrl,
                onDismiss = actions.onDismissReveal,
            )
        }
    }
}

@Composable
private fun KeyListPane(
    keys: List<ApiKey>,
    isCreating: Boolean,
    endpointUrl: String,
    onCreate: (String) -> Unit,
    onRevoke: (String) -> Unit,
) {
    val spacing = RecompTheme.spacing
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = spacing.gutter, vertical = spacing.lg),
        verticalArrangement = Arrangement.spacedBy(spacing.lg),
    ) {
        if (keys.isEmpty()) {
            item { OnboardingIntro(endpointUrl = endpointUrl) }
        }
        item { CreateKeyForm(isCreating = isCreating, onCreate = onCreate) }
        item { Text(text = "YOUR KEYS", style = RecompTheme.typography.kicker, color = RecompTheme.colors.volt) }
        if (keys.isEmpty()) {
            item { EmptyState() }
        } else {
            items(keys, key = { it.id }) { key ->
                ApiKeyRow(key = key, onRevoke = { onRevoke(key.id) })
            }
        }
    }
}

/**
 * The zero-key first-run panel that opens onboarding: it explains the next step —
 * mint a key below, then connect an MCP client — and shows the hosted endpoint the
 * client will connect to, so the user knows what they are wiring up before the
 * full reveal+config flow appears on creation.
 */
@Composable
private fun OnboardingIntro(endpointUrl: String) {
    val colors = RecompTheme.colors
    val type = RecompTheme.typography
    val spacing = RecompTheme.spacing
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(colors.panel, RecompTheme.shapes.xl)
                .border(spacing.border, colors.line, RecompTheme.shapes.xl)
                .padding(spacing.cardInsetH),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        Text(text = "CONNECT AN MCP CLIENT", style = type.kicker, color = colors.volt)
        Text(
            text =
                "Create your first key below. You will then get the endpoint, the key, " +
                    "and a ready-to-paste config to connect your MCP client.",
            style = type.body,
            color = colors.dim,
        )
        Text(text = "ENDPOINT", style = type.label, color = colors.dim)
        Text(text = endpointUrl, style = type.loadValue, color = colors.volt)
    }
}

@Composable
private fun KeysTopBar(onBack: () -> Unit) {
    val colors = RecompTheme.colors
    val type = RecompTheme.typography
    val spacing = RecompTheme.spacing
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(colors.bg)
                .padding(horizontal = spacing.gutter, vertical = spacing.md),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = "API KEYS", style = type.kicker, color = colors.volt)
        Text(
            text = "BACK",
            style = type.label,
            color = colors.dim,
            modifier =
                Modifier
                    .border(spacing.border, colors.line, RecompTheme.shapes.sm)
                    .clickable(onClick = onBack)
                    .padding(horizontal = spacing.lg, vertical = spacing.sm),
        )
    }
}

@Composable
private fun EmptyState() {
    val colors = RecompTheme.colors
    val type = RecompTheme.typography
    val spacing = RecompTheme.spacing
    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        Text(text = "No keys yet.", style = type.body, color = colors.mutedEmpty)
        Text(
            text = "Create one above, then paste it into your MCP client to connect.",
            style = type.body,
            color = colors.mutedEmpty,
        )
    }
}

@Composable
private fun CenterSpinner() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = RecompTheme.colors.volt)
    }
}

@Composable
private fun ErrorPane(
    message: String,
    onRetry: () -> Unit,
) {
    val colors = RecompTheme.colors
    val type = RecompTheme.typography
    val spacing = RecompTheme.spacing
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = spacing.gutter),
        verticalArrangement = Arrangement.spacedBy(spacing.md, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = message, style = type.body, color = colors.legs)
        Text(
            text = "RETRY",
            style = type.label,
            color = colors.bg,
            modifier =
                Modifier
                    .background(colors.volt, RecompTheme.shapes.md)
                    .clickable(onClick = onRetry)
                    .padding(horizontal = spacing.lg, vertical = spacing.sm),
        )
    }
}
