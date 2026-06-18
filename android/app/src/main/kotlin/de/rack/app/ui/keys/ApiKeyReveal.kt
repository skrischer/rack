package de.rack.app.ui.keys

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import de.rack.app.ui.theme.RecompGhostButton
import de.rack.app.ui.theme.RecompPrimaryButton
import de.rack.app.ui.theme.RecompTheme
import de.rack.app.ui.theme.recompClick

/** Scrim opacity over the key list while the reveal modal is open (kit `.overlay`). */
private const val SCRIM_ALPHA = 0.72f

/**
 * The one-time key-reveal modal (kit `key-reveal.html`): a scrim over the key list
 * with a centered card that shows the freshly-minted [plaintext] exactly once,
 * alongside the hosted [endpointUrl] and a ready-to-paste MCP-client config built
 * from the same endpoint constant the admin calls use.
 *
 * The primary action copies the secret; the endpoint and config each copy
 * independently. [onDismiss] discards the plaintext irrecoverably — the server
 * cannot return it again — so leaving via "Fertig" is the only exit, and tapping
 * the scrim is intentionally inert to avoid losing the key by accident. No business
 * logic lives here.
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
    val clipboard = LocalClipboardManager.current
    val snippet = mcpClientConfigSnippet(endpointUrl, plaintext)

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(colors.bg.copy(alpha = SCRIM_ALPHA))
                .recompClick(onClick = {})
                .padding(spacing.xxl),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(colors.panel, RecompTheme.shapes.xl)
                    .border(spacing.border, colors.line, RecompTheme.shapes.xl)
                    .verticalScroll(rememberScrollState())
                    .padding(spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(spacing.lg),
        ) {
            Text(text = "KEY ERSTELLT", style = type.noteHeading, color = colors.volt)
            Text(
                text =
                    "Dieser Schlüssel wird nur jetzt angezeigt. Kopiere ihn und bewahre ihn " +
                        "sicher auf — er kann später nicht erneut abgerufen werden.",
                style = type.body,
                color = colors.dim,
            )
            RevealField(label = "SCHLÜSSEL", value = plaintext)
            RevealField(
                label = "MCP-ENDPUNKT",
                value = endpointUrl,
                onCopy = { clipboard.setText(AnnotatedString(endpointUrl)) },
            )
            RevealField(
                label = "MCP-CLIENT-CONFIG",
                value = snippet,
                onCopy = { clipboard.setText(AnnotatedString(snippet)) },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                RecompPrimaryButton(
                    text = "Kopieren",
                    onClick = { clipboard.setText(AnnotatedString(plaintext)) },
                    modifier = Modifier.weight(1f),
                )
                RecompGhostButton(text = "Fertig", onClick = onDismiss)
            }
        }
    }
}

/**
 * A labelled mono code box (kit `.code`): the [label] cap with an optional inline
 * "Kopieren" affordance, over the [value] in a bordered box. The secret field omits
 * its own copy — the modal's primary action copies it.
 */
@Composable
private fun RevealField(
    label: String,
    value: String,
    onCopy: (() -> Unit)? = null,
) {
    val colors = RecompTheme.colors
    val type = RecompTheme.typography
    val spacing = RecompTheme.spacing
    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = label, style = type.label, color = colors.dim)
            if (onCopy != null) {
                Text(
                    text = "KOPIEREN",
                    style = type.label,
                    color = colors.voltDim,
                    modifier = Modifier.recompClick(onClick = onCopy),
                )
            }
        }
        Text(
            text = value,
            style = type.loadValue,
            color = colors.txt,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(colors.bg, RecompTheme.shapes.sm)
                    .border(spacing.border, colors.line, RecompTheme.shapes.sm)
                    .padding(spacing.lg),
        )
    }
}
