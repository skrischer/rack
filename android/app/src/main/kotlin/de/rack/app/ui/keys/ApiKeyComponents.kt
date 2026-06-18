package de.rack.app.ui.keys

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import de.rack.app.domain.ApiKey
import de.rack.app.ui.theme.RecompBadge
import de.rack.app.ui.theme.RecompBadgeStyle
import de.rack.app.ui.theme.RecompGhostButton
import de.rack.app.ui.theme.RecompPrimaryButton
import de.rack.app.ui.theme.RecompTheme

/** Maximum key-name length, mirroring the MCP's 1-64 char Zod validation. */
private const val MAX_NAME_LENGTH = 64

/**
 * The "Neuen Key erstellen" card (kit `.card`): a name field over a volt CREATE
 * button; mints a key and clears the field on submit. Presentational only — the
 * create event flows upward to the ViewModel.
 */
@Composable
fun CreateKeyForm(
    isCreating: Boolean,
    onCreate: (String) -> Unit,
) {
    val colors = RecompTheme.colors
    val type = RecompTheme.typography
    val spacing = RecompTheme.spacing
    var name by rememberSaveable { mutableStateOf("") }
    val canSubmit = name.isNotBlank() && !isCreating

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(colors.panel, RecompTheme.shapes.xl)
                .border(spacing.border, colors.line, RecompTheme.shapes.xl)
                .padding(spacing.cardInsetH),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        Text(text = "NEUEN KEY ERSTELLEN", style = type.kicker, color = colors.volt)
        OutlinedTextField(
            value = name,
            onValueChange = { if (it.length <= MAX_NAME_LENGTH) name = it },
            enabled = !isCreating,
            singleLine = true,
            label = { Text(text = "NAME", style = type.label) },
            textStyle = type.intro,
            shape = RecompTheme.shapes.sm,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions =
                KeyboardActions(
                    onDone = {
                        if (canSubmit) {
                            onCreate(name.trim())
                            name = ""
                        }
                    },
                ),
            colors = keyFieldColors(),
            modifier = Modifier.fillMaxWidth(),
        )
        RecompPrimaryButton(
            text = if (isCreating) "Erstellt…" else "Erstellen",
            onClick = {
                onCreate(name.trim())
                name = ""
            },
            enabled = canSubmit,
            fillMaxWidth = true,
        )
    }
}

@Composable
private fun keyFieldColors() =
    TextFieldDefaults.colors(
        focusedContainerColor = RecompTheme.colors.panel,
        unfocusedContainerColor = RecompTheme.colors.panel,
        disabledContainerColor = RecompTheme.colors.panel,
        focusedTextColor = RecompTheme.colors.txt,
        unfocusedTextColor = RecompTheme.colors.txt,
        cursorColor = RecompTheme.colors.volt,
        focusedIndicatorColor = RecompTheme.colors.volt,
        unfocusedIndicatorColor = RecompTheme.colors.line,
        focusedLabelColor = RecompTheme.colors.volt,
        unfocusedLabelColor = RecompTheme.colors.dim,
    )

/**
 * One key row (kit `.row`): name over its created / last-used metadata and masked
 * prefix, with a ghost Revoke action — or a "Widerrufen" badge once revoked. Never
 * shows the secret; the masked prefix is the only key fragment rendered.
 */
@Composable
fun ApiKeyRow(
    key: ApiKey,
    onRevoke: () -> Unit,
) {
    val colors = RecompTheme.colors
    val type = RecompTheme.typography
    val spacing = RecompTheme.spacing
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(colors.panel, RecompTheme.shapes.xl)
                .border(spacing.border, colors.line, RecompTheme.shapes.xl)
                .padding(spacing.cardInsetH),
        horizontalArrangement = Arrangement.spacedBy(spacing.md),
        verticalAlignment = Alignment.Top,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(spacing.xxs),
        ) {
            Text(
                text = key.name?.takeIf { it.isNotBlank() } ?: "Unbenannter Key",
                style = type.exerciseName,
                color = if (key.revoked) colors.mutedEmpty else colors.txt,
            )
            Text(text = metaLine(key), style = type.caption, color = colors.dim)
            Text(text = maskedPrefix(key.keyPrefix), style = type.loadValue, color = colors.dim)
        }
        KeyStatus(revoked = key.revoked, onRevoke = onRevoke)
    }
}

@Composable
private fun KeyStatus(
    revoked: Boolean,
    onRevoke: () -> Unit,
) {
    if (revoked) {
        RecompBadge(text = "Widerrufen", style = RecompBadgeStyle.Legs)
    } else {
        RecompGhostButton(text = "Revoke", onClick = onRevoke)
    }
}

/** Show a recognizable fragment masked, never the secret: `rack_ab12…`. */
private fun maskedPrefix(prefix: String?): String = prefix?.takeIf { it.isNotBlank() }?.let { "$it…" } ?: "…"

/** Trim an ISO timestamp to its date portion for the compact list rows. */
private fun shortDate(iso: String): String = iso.substringBefore('T').ifBlank { iso }

/** "erstellt {date} · zuletzt genutzt {date}" (or "nie genutzt") — the row metadata line. */
private fun metaLine(key: ApiKey): String = "erstellt ${shortDate(key.createdAt)} · ${lastUsedLabel(key.lastUsedAt)}"

private fun lastUsedLabel(lastUsedAt: String?): String =
    lastUsedAt?.takeIf { it.isNotBlank() }?.let { "zuletzt genutzt ${shortDate(it)}" } ?: "nie genutzt"
