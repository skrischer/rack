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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import de.rack.app.domain.ApiKey
import de.rack.app.ui.theme.RecompEmpty
import de.rack.app.ui.theme.RecompError
import de.rack.app.ui.theme.RecompLoading
import de.rack.app.ui.theme.RecompTheme

/**
 * Recomp key-management + onboarding screen: lists the signed-in user's rack-MCP
 * API keys, mints a new named one, walks a first-time user through connecting an
 * MCP client, and revokes existing keys. Purely presentational — it renders
 * [state] and emits create / revoke / retry / back events upward; the one-time
 * reveal modal is shown from [ApiKeyReveal] while [ApiKeyState.revealedKey] is
 * non-null. No Supabase access and no business logic live here.
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
                    is ApiKeyListState.Loading -> RecompLoading()
                    is ApiKeyListState.Error -> RecompError(message = list.message, onRetry = actions.onRetry)
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
        item { McpIntroNote(endpointUrl = endpointUrl) }
        item { CreateKeyForm(isCreating = isCreating, onCreate = onCreate) }
        item { SectionTitle(text = "DEINE KEYS") }
        if (keys.isEmpty()) {
            item {
                RecompEmpty(text = "Noch keine Keys.\nErstelle oben einen und verbinde deinen MCP-Client.")
            }
        } else {
            items(keys, key = { it.id }) { key ->
                ApiKeyRow(key = key, onRevoke = { onRevoke(key.id) })
            }
        }
        item { FooterNote() }
    }
}

/**
 * The `rack-mcp` intro note (kit `.note`): explains that any MCP client connects
 * with a personal key to author plans live in the app, and shows the hosted
 * [endpointUrl] to paste into the client — the same value the reveal modal copies.
 */
@Composable
private fun McpIntroNote(endpointUrl: String) {
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
        Text(text = "RACK-MCP", style = type.noteHeading, color = colors.volt)
        Text(
            text =
                "Verbinde jeden MCP-Client — etwa Claude Desktop — mit einem persönlichen Key. " +
                    "Dein Agent entwirft Pläne und Visualisierungen und schreibt sie direkt in die App.",
            style = type.body,
            color = colors.txt,
        )
        Text(
            text =
                "Änderungen erscheinen live und hervorgehoben, sobald der Agent schreibt. " +
                    "Trage diesen Endpunkt im Client ein:",
            style = type.body,
            color = colors.txt,
        )
        Text(
            text = endpointUrl,
            style = type.loadValue,
            color = colors.txt,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(colors.bg, RecompTheme.shapes.sm)
                    .border(spacing.border, colors.line, RecompTheme.shapes.sm)
                    .padding(spacing.md),
        )
    }
}

/** A standalone list-section heading in the kit's muted mono voice (`.section-title`). */
@Composable
private fun SectionTitle(text: String) {
    Text(text = text, style = RecompTheme.typography.label, color = RecompTheme.colors.dim)
}

/** The reassuring footer line (kit `.foot`): keys are hashed; the full value shows once. */
@Composable
private fun FooterNote() {
    Text(
        text = "Keys werden gehasht gespeichert · voller Wert nur einmal sichtbar",
        style = RecompTheme.typography.caption,
        color = RecompTheme.colors.dim,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
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
        Text(text = "API-KEYS", style = type.kicker, color = colors.volt)
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
