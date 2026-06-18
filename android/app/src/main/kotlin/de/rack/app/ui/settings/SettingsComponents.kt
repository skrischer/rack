package de.rack.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import de.rack.app.ui.theme.RecompTheme

/** Maximum display-name length kept short for the compact profile row. */
private const val MAX_DISPLAY_NAME_LENGTH = 40

/** A panel-styled settings section: a volt kicker title over its [content]. */
@Composable
fun SettingsSection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val colors = RecompTheme.colors
    val spacing = RecompTheme.spacing
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .background(colors.panel, RecompTheme.shapes.xl)
                .border(spacing.border, colors.line, RecompTheme.shapes.xl)
                .padding(spacing.cardInsetH),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        Text(text = title, style = RecompTheme.typography.kicker, color = colors.volt)
        content()
    }
}

/** One selectable option a [SegmentedToggle] renders. */
data class ToggleOption<T>(
    val value: T,
    val label: String,
)

/**
 * A row of mutually exclusive pills; the [selected] one fills volt with on-volt
 * [RecompTheme.colors.bg] text, the rest read as muted panel chips. Used for the
 * weight-unit and theme choices.
 */
@Composable
fun <T> SegmentedToggle(
    options: List<ToggleOption<T>>,
    selected: T,
    onSelect: (T) -> Unit,
) {
    val spacing = RecompTheme.spacing
    Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
        options.forEach { option ->
            TogglePill(
                label = option.label,
                selected = option.value == selected,
                onClick = { onSelect(option.value) },
            )
        }
    }
}

@Composable
private fun TogglePill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = RecompTheme.colors
    val spacing = RecompTheme.spacing
    val background = if (selected) colors.volt else colors.panelElevated
    val textColor = if (selected) colors.bg else colors.dim
    Text(
        text = label,
        style = RecompTheme.typography.label,
        color = textColor,
        modifier =
            Modifier
                .background(background, RecompTheme.shapes.lg)
                .border(spacing.border, colors.line, RecompTheme.shapes.lg)
                .clickable(onClick = onClick)
                .padding(horizontal = spacing.lg, vertical = spacing.sm),
    )
}

/**
 * A labelled stepper row for one rest-timer default: minus / monospace value / plus.
 * Edits stay within [RestSeconds] bounds and step by [RestSeconds.STEP] so the
 * stored value is always a clean increment the timer feature can read.
 */
@Composable
fun RestDefaultRow(
    label: String,
    seconds: Int,
    onChange: (Int) -> Unit,
) {
    val colors = RecompTheme.colors
    val type = RecompTheme.typography
    val spacing = RecompTheme.spacing
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = type.label, color = colors.dim)
        Row(
            horizontalArrangement = Arrangement.spacedBy(spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StepperButton(symbol = "-", onClick = { onChange(RestSeconds.decrement(seconds)) })
            Text(text = "${seconds}s", style = type.loadValue, color = colors.volt)
            StepperButton(symbol = "+", onClick = { onChange(RestSeconds.increment(seconds)) })
        }
    }
}

@Composable
private fun StepperButton(
    symbol: String,
    onClick: () -> Unit,
) {
    val colors = RecompTheme.colors
    val spacing = RecompTheme.spacing
    Box(
        modifier =
            Modifier
                .size(STEPPER_SIZE)
                .background(colors.panelElevated, RecompTheme.shapes.sm)
                .border(spacing.border, colors.line, RecompTheme.shapes.sm)
                .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = symbol, style = RecompTheme.typography.loadValue, color = colors.txt)
    }
}

/**
 * The editable display-name field. Keeps local edit state so it does not write on
 * every keystroke; it persists via [onCommit] on the Done action and on focus loss,
 * the same deliberate-commit pattern as the key-name form.
 */
@Composable
fun DisplayNameField(
    displayName: String,
    onCommit: (String) -> Unit,
) {
    val type = RecompTheme.typography
    var text by rememberSaveable(displayName) { mutableStateOf(displayName) }
    OutlinedTextField(
        value = text,
        onValueChange = { if (it.length <= MAX_DISPLAY_NAME_LENGTH) text = it },
        singleLine = true,
        label = { Text(text = "DISPLAY NAME", style = type.label) },
        textStyle = type.intro,
        shape = RecompTheme.shapes.sm,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onCommit(text.trim()) }),
        colors = settingsFieldColors(),
        modifier =
            Modifier
                .fillMaxWidth()
                .onFocusChanged { focus ->
                    if (!focus.isFocused && text.trim() != displayName) onCommit(text.trim())
                },
    )
}

/** The read-only Auth email row: a caption label over the account address. */
@Composable
fun EmailRow(email: String) {
    val colors = RecompTheme.colors
    val type = RecompTheme.typography
    val spacing = RecompTheme.spacing
    Column(verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
        Text(text = "ACCOUNT EMAIL", style = type.label, color = colors.dim)
        Text(
            text = email.takeIf { it.isNotBlank() } ?: "Unknown",
            style = type.loadValue,
            color = colors.txt,
        )
    }
}

@Composable
private fun settingsFieldColors() =
    TextFieldDefaults.colors(
        focusedContainerColor = RecompTheme.colors.panelElevated,
        unfocusedContainerColor = RecompTheme.colors.panelElevated,
        focusedTextColor = RecompTheme.colors.txt,
        unfocusedTextColor = RecompTheme.colors.txt,
        cursorColor = RecompTheme.colors.volt,
        focusedIndicatorColor = RecompTheme.colors.volt,
        unfocusedIndicatorColor = RecompTheme.colors.line,
        focusedLabelColor = RecompTheme.colors.volt,
        unfocusedLabelColor = RecompTheme.colors.dim,
    )

private val STEPPER_SIZE = 36.dp
