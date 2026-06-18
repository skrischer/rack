package de.rack.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.ImeAction
import de.rack.app.ui.theme.RecompDivider
import de.rack.app.ui.theme.RecompStepper
import de.rack.app.ui.theme.RecompTheme

/** Maximum display-name length kept short for the compact profile row. */
private const val MAX_DISPLAY_NAME_LENGTH = 40

/** Seconds in a minute, for the `m:ss` rest-time formatting. */
private const val SECONDS_PER_MINUTE = 60

/**
 * A Recomp settings card (kit `.card`): a panel-elevated header band over a stack of
 * divided rows. The card clips to the `xl` radius so the head band and row dividers stay
 * inside the rounded outline. Drop [SettingsRow] / [SettingsBlock] children into [content].
 */
@Composable
fun SettingsCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val colors = RecompTheme.colors
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RecompTheme.shapes.xl)
                .background(colors.panel)
                .border(RecompTheme.spacing.border, colors.line, RecompTheme.shapes.xl),
    ) {
        SettingsCardHead(title)
        content()
    }
}

@Composable
private fun SettingsCardHead(title: String) {
    val colors = RecompTheme.colors
    val spacing = RecompTheme.spacing
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(colors.panelElevated)
                .padding(horizontal = spacing.cardInsetH, vertical = spacing.lg),
    ) {
        Text(text = title, style = RecompTheme.typography.label, color = colors.txt)
    }
}

/**
 * One card row (kit `.row`): a leading 1px divider, a key [label], and a right-aligned
 * [trailing] control (segmented toggle, stepper, switch, value). The divider sits above the
 * row so the head and successive rows read as hairline-separated bands.
 */
@Composable
fun SettingsRow(
    label: String,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit,
) {
    val colors = RecompTheme.colors
    val spacing = RecompTheme.spacing
    Column(modifier = modifier.fillMaxWidth()) {
        RecompDivider()
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.cardInsetH, vertical = spacing.rowInsetV),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = label, style = RecompTheme.typography.label, color = colors.txt)
            trailing()
        }
    }
}

/**
 * A full-width card block (kit `.row` with stacked content): a leading divider over a
 * single [content] slot, for rows that span the row width (the weekday chip rail, the
 * editable display-name field) rather than a label/control pair.
 */
@Composable
fun SettingsBlock(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val spacing = RecompTheme.spacing
    Column(modifier = modifier.fillMaxWidth()) {
        RecompDivider()
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.cardInsetH, vertical = spacing.rowInsetV),
        ) {
            content()
        }
    }
}

/**
 * The rest-default control: a [RecompStepper] over [seconds] formatted as `m:ss`, clamped
 * and stepped by [RestSeconds] so the stored value is always a clean increment the timer
 * feature can read.
 */
@Composable
fun RestStepper(
    seconds: Int,
    onChange: (Int) -> Unit,
) {
    RecompStepper(
        value = formatRestTime(seconds),
        onDecrement = { onChange(RestSeconds.decrement(seconds)) },
        onIncrement = { onChange(RestSeconds.increment(seconds)) },
    )
}

/** Formats rest seconds as `m:ss` (e.g. 150 -> "2:30"), the kit's `.step-val` voice. */
fun formatRestTime(seconds: Int): String =
    "${seconds / SECONDS_PER_MINUTE}:${(seconds % SECONDS_PER_MINUTE).toString().padStart(2, '0')}"

/**
 * The editable display-name field. Keeps local edit state so it does not write on every
 * keystroke; it persists via [onCommit] on the Done action and on focus loss, the same
 * deliberate-commit pattern as the key-name form.
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
        label = { Text(text = "Anzeigename", style = type.label) },
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

/**
 * Pure UI clamping for the rest-default stepper: edits stay within [MIN]..[MAX] and move
 * by [STEP] seconds so a stored default is always a clean increment. This is a presentation
 * constraint on the control, not the timer's domain logic.
 */
object RestSeconds {
    const val MIN = 15
    const val MAX = 600
    const val STEP = 15

    fun increment(seconds: Int): Int = (seconds + STEP).coerceAtMost(MAX)

    fun decrement(seconds: Int): Int = (seconds - STEP).coerceAtLeast(MIN)
}
