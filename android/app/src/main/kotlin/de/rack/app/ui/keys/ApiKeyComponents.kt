package de.rack.app.ui.keys

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.unit.dp
import de.rack.app.domain.ApiKey
import de.rack.app.ui.theme.RecompTheme

/** Maximum key-name length, mirroring the MCP's 1-64 char Zod validation. */
private const val MAX_NAME_LENGTH = 64

/** Name + create button; mints a key and clears the field on submit. */
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

    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        Text(text = "NEW KEY", style = type.kicker, color = colors.volt)
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
        PrimaryButton(
            label = if (isCreating) "CREATING" else "CREATE KEY",
            enabled = canSubmit,
            onClick = {
                onCreate(name.trim())
                name = ""
            },
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

/** One key row: name, masked prefix (mono), created/last-used, revoked state + revoke. */
@Composable
fun ApiKeyRow(
    key: ApiKey,
    onRevoke: () -> Unit,
) {
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
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = key.name?.takeIf { it.isNotBlank() } ?: "Unnamed key",
                style = type.exerciseName,
                color = if (key.revoked) colors.mutedEmpty else colors.txt,
            )
            KeyStatus(key = key, onRevoke = onRevoke)
        }
        Text(text = maskedPrefix(key.keyPrefix), style = type.loadValue, color = colors.volt)
        Text(text = "CREATED ${shortDate(key.createdAt)}", style = type.caption, color = colors.dim)
        Text(text = lastUsedLabel(key.lastUsedAt), style = type.caption, color = colors.dim)
    }
}

@Composable
private fun KeyStatus(
    key: ApiKey,
    onRevoke: () -> Unit,
) {
    val colors = RecompTheme.colors
    val type = RecompTheme.typography
    val spacing = RecompTheme.spacing
    if (key.revoked) {
        Text(
            text = "REVOKED",
            style = type.label,
            color = colors.legs,
            modifier =
                Modifier
                    .background(colors.warningBg, RecompTheme.shapes.sm)
                    .border(spacing.border, colors.legs, RecompTheme.shapes.sm)
                    .padding(horizontal = spacing.md, vertical = spacing.xxs),
        )
    } else {
        Text(
            text = "REVOKE",
            style = type.label,
            color = colors.dim,
            modifier =
                Modifier
                    .border(spacing.border, colors.line, RecompTheme.shapes.sm)
                    .clickable(onClick = onRevoke)
                    .padding(horizontal = spacing.md, vertical = spacing.xxs),
        )
    }
}

/** Volt-filled primary button matching the login/plan CTA treatment. */
@Composable
fun PrimaryButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = RecompTheme.colors
    val type = RecompTheme.typography
    val fill = if (enabled) colors.volt else colors.voltDim
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(BUTTON_HEIGHT)
                .background(fill, RecompTheme.shapes.md)
                .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, style = type.label, color = colors.bg)
    }
}

/** Show a recognizable fragment masked, never the secret: `rack_ab12…`. */
private fun maskedPrefix(prefix: String?): String = prefix?.takeIf { it.isNotBlank() }?.let { "$it…" } ?: "…"

/** Trim an ISO timestamp to its date portion for the compact list rows. */
private fun shortDate(iso: String): String = iso.substringBefore('T').ifBlank { iso }

private fun lastUsedLabel(lastUsedAt: String?): String =
    lastUsedAt?.takeIf { it.isNotBlank() }?.let { "LAST USED ${shortDate(it)}" } ?: "NEVER USED"

private val BUTTON_HEIGHT = 52.dp
