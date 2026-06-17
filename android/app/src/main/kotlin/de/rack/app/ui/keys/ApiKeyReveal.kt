package de.rack.app.ui.keys

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import de.rack.app.ui.theme.RecompTheme

/**
 * The onboarding flow shown over the key list right after a key is minted: it
 * walks a first-time user from a freshly created key to a working MCP-client
 * connection. It presents the freshly-minted [plaintext], the hosted
 * [endpointUrl], and a ready-to-paste Streamable-HTTP config [snippet] — each
 * with its own copy-to-clipboard action — plus the one-time "you will not see
 * this again" warning for the secret.
 *
 * The snippet is built from the same endpoint constant the app's admin calls use,
 * so pasting it into a real client connects. [onDismiss] clears the plaintext
 * irrecoverably — the server cannot return it again — so leaving discards it. No
 * business logic lives here.
 */
@Composable
fun ApiKeyReveal(
    plaintext: String,
    endpointUrl: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = RecompTheme.colors
    val type = RecompTheme.typography
    val spacing = RecompTheme.spacing
    val snippet = mcpClientConfigSnippet(endpointUrl, plaintext)

    Box(modifier = modifier.fillMaxSize().background(colors.bg)) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(spacing.gutter),
            verticalArrangement = Arrangement.spacedBy(spacing.lg),
        ) {
            Text(text = "CONNECT YOUR MCP CLIENT", style = type.kicker, color = colors.volt)
            Text(
                text =
                    "Your key is ready. Add the endpoint and key to your MCP client, " +
                        "or paste the config snippet below to connect.",
                style = type.body,
                color = colors.dim,
            )
            WarningBand()
            CopyableField(
                label = "YOUR KEY",
                value = plaintext,
                copyLabel = "COPY KEY",
            )
            CopyableField(
                label = "ENDPOINT URL",
                value = endpointUrl,
                copyLabel = "COPY URL",
            )
            CopyableField(
                label = "MCP CLIENT CONFIG",
                value = snippet,
                copyLabel = "COPY CONFIG",
            )
            Text(
                text = "DONE",
                style = type.label,
                color = colors.dim,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .border(spacing.border, colors.line, RecompTheme.shapes.md)
                        .clickable(onClick = onDismiss)
                        .padding(vertical = spacing.md),
            )
        }
    }
}

/**
 * A labelled mono value box with its own copy-to-clipboard action; the single
 * onboarding building block, reused for the key, the endpoint URL, and the
 * config snippet so each field copies independently.
 */
@Composable
private fun CopyableField(
    label: String,
    value: String,
    copyLabel: String,
) {
    val colors = RecompTheme.colors
    val type = RecompTheme.typography
    val spacing = RecompTheme.spacing
    val clipboard = LocalClipboardManager.current

    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        Text(text = label, style = type.label, color = colors.dim)
        Text(
            text = value,
            style = type.loadValue,
            color = colors.volt,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(colors.panel, RecompTheme.shapes.sm)
                    .border(spacing.border, colors.line, RecompTheme.shapes.sm)
                    .padding(spacing.lg),
        )
        PrimaryButton(
            label = copyLabel,
            enabled = true,
            onClick = { clipboard.setText(AnnotatedString(value)) },
        )
    }
}

@Composable
private fun WarningBand() {
    val colors = RecompTheme.colors
    val type = RecompTheme.typography
    val spacing = RecompTheme.spacing
    Text(
        text = "Copy this key now. You will not be able to see it again.",
        style = type.body,
        color = colors.warningText,
        modifier =
            Modifier
                .fillMaxWidth()
                .background(colors.warningBg, RecompTheme.shapes.sm)
                .border(spacing.border, colors.legs, RecompTheme.shapes.sm)
                .padding(spacing.lg),
    )
}
