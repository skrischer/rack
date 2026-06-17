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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import de.rack.app.ui.theme.RecompTheme

/**
 * The one-time plaintext reveal, shown over the key list while the freshly minted
 * key is in transient ViewModel state. It presents the secret in mono, a
 * copy-to-clipboard action, and an explicit "you will not see this again" warning.
 * [onDismiss] clears the plaintext irrecoverably — the server cannot return it
 * again — so leaving discards it. No business logic lives here.
 */
@Composable
fun ApiKeyReveal(
    plaintext: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = RecompTheme.colors
    val type = RecompTheme.typography
    val spacing = RecompTheme.spacing
    val clipboard = LocalClipboardManager.current

    Box(
        modifier = modifier.fillMaxSize().background(colors.bg).padding(spacing.gutter),
        contentAlignment = Alignment.Center,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.lg)) {
            Text(text = "NEW KEY CREATED", style = type.kicker, color = colors.volt)
            WarningBand()
            SecretBox(plaintext = plaintext)
            PrimaryButton(
                label = "COPY KEY",
                enabled = true,
                onClick = { clipboard.setText(AnnotatedString(plaintext)) },
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

@Composable
private fun SecretBox(plaintext: String) {
    val colors = RecompTheme.colors
    val type = RecompTheme.typography
    val spacing = RecompTheme.spacing
    Text(
        text = plaintext,
        style = type.loadValue,
        color = colors.volt,
        modifier =
            Modifier
                .fillMaxWidth()
                .background(colors.panel, RecompTheme.shapes.sm)
                .border(spacing.border, colors.line, RecompTheme.shapes.sm)
                .padding(spacing.lg),
    )
}
