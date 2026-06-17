package de.rack.app.ui.logging

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import de.rack.app.domain.SetLog
import de.rack.app.ui.theme.RecompTheme
import de.rack.app.ui.theme.agentHighlight

/**
 * The per-exercise logging row from the prototype: the "last time" summary (tap to
 * disclose dated history), then a single kg input and one reps input per target
 * set, and a log action. Purely presentational — it renders [state] and forwards
 * edit / toggle / log events through [handlers] keyed by [exerciseId]; no Supabase
 * access or business logic here.
 */
@Composable
fun LoggingRow(
    exerciseId: String,
    state: ExerciseLogState,
    handlers: LoggingHandlers,
    modifier: Modifier = Modifier,
) {
    val spacing = RecompTheme.spacing
    Column(
        modifier = modifier.fillMaxWidth().padding(top = spacing.sm),
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        LastTimeSummary(state = state, onToggleHistory = { handlers.onToggleHistory(exerciseId) })
        if (state.historyExpanded && state.history.isNotEmpty()) {
            HistoryList(history = state.history, highlightedIds = state.highlightedIds)
        }
        InputsRow(
            state = state,
            onWeightChange = { value -> handlers.onWeightChange(exerciseId, value) },
            onRepChange = { index, value -> handlers.onRepChange(exerciseId, index, value) },
        )
        LogButton(state = state, onLog = { handlers.onLog(exerciseId) })
    }
}

@Composable
private fun LastTimeSummary(
    state: ExerciseLogState,
    onToggleHistory: () -> Unit,
) {
    val colors = RecompTheme.colors
    val type = RecompTheme.typography
    val last = state.lastLog
    if (last == null) {
        Text(text = "No entry yet", style = type.lastTime, color = colors.mutedEmpty)
        return
    }
    Row(
        modifier =
            Modifier
                .agentHighlight(highlighted = last.id in state.highlightedIds, shape = RecompTheme.shapes.sm)
                .clickable(onClick = onToggleHistory)
                .padding(horizontal = RecompTheme.spacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = "Last time · ${summaryLine(last)}", style = type.lastTime, color = colors.dim)
        Text(text = "  ▾", style = type.lastTime, color = colors.voltDim)
    }
}

@Composable
private fun HistoryList(
    history: List<SetLog>,
    highlightedIds: Set<String>,
) {
    val colors = RecompTheme.colors
    val type = RecompTheme.typography
    val spacing = RecompTheme.spacing
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(colors.bg, RecompTheme.shapes.sm)
                .border(spacing.border, colors.line, RecompTheme.shapes.sm)
                .padding(horizontal = spacing.sm, vertical = spacing.sm),
        verticalArrangement = Arrangement.spacedBy(spacing.xxs),
    ) {
        history.forEach { entry ->
            Text(
                text = summaryLine(entry),
                style = type.history,
                color = colors.dim,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .agentHighlight(highlighted = entry.id in highlightedIds, shape = RecompTheme.shapes.sm),
            )
        }
    }
}

@Composable
private fun InputsRow(
    state: ExerciseLogState,
    onWeightChange: (String) -> Unit,
    onRepChange: (Int, String) -> Unit,
) {
    val spacing = RecompTheme.spacing
    Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm), verticalAlignment = Alignment.Bottom) {
        LogField(label = "KG", value = state.weightInput, keyboard = KeyboardType.Decimal, onChange = onWeightChange)
        repeat(state.setCount) { index ->
            LogField(
                label = "${index + 1}",
                value = state.repsInputs.getOrElse(index) { "" },
                keyboard = KeyboardType.Number,
                onChange = { onRepChange(index, it) },
            )
        }
    }
}

@Composable
private fun LogField(
    label: String,
    value: String,
    keyboard: KeyboardType,
    onChange: (String) -> Unit,
) {
    val colors = RecompTheme.colors
    val type = RecompTheme.typography
    val spacing = RecompTheme.spacing
    Column(
        modifier = Modifier.width(spacing.dayChip + spacing.xxl),
        verticalArrangement = Arrangement.spacedBy(spacing.xxs),
    ) {
        Text(text = label.uppercase(), style = type.caption, color = colors.dim)
        TextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = LocalTextStyle.current.merge(type.intro.copy(color = colors.txt)),
            keyboardOptions = KeyboardOptions(keyboardType = keyboard),
            colors = logFieldColors(),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun LogButton(
    state: ExerciseLogState,
    onLog: () -> Unit,
) {
    val colors = RecompTheme.colors
    val type = RecompTheme.typography
    val spacing = RecompTheme.spacing
    val enabled = state.hasInput && !state.logging
    val background = if (enabled) colors.volt else colors.panelElevated
    val textColor = if (enabled) colors.bg else colors.mutedEmpty
    Text(
        text = "✓ LOG SET",
        style = type.label,
        color = textColor,
        modifier =
            Modifier
                .background(background, RecompTheme.shapes.md)
                .then(if (enabled) Modifier.clickable(onClick = onLog) else Modifier)
                .padding(horizontal = spacing.lg, vertical = spacing.md),
    )
}

@Composable
private fun logFieldColors() =
    TextFieldDefaults.colors(
        focusedContainerColor = RecompTheme.colors.bg,
        unfocusedContainerColor = RecompTheme.colors.bg,
        disabledContainerColor = RecompTheme.colors.bg,
        focusedIndicatorColor = RecompTheme.colors.volt,
        unfocusedIndicatorColor = RecompTheme.colors.line,
        cursorColor = RecompTheme.colors.volt,
    )

/** "weight kg · r1/r2/r3" for the last-time and history lines (· when empty). */
private fun summaryLine(log: SetLog): String {
    val weight = log.weight?.let { formatWeight(it) } ?: "–"
    val reps = log.reps.filter { it > 0 }.joinToString(separator = "/").ifEmpty { "–" }
    val date = log.date?.takeIf { it.isNotBlank() } ?: log.loggedAt.take(DATE_PREFIX_LENGTH)
    return "$date: $weight kg · $reps"
}

private fun formatWeight(weight: Double): String =
    if (weight % 1.0 == 0.0) weight.toLong().toString() else weight.toString()

private const val DATE_PREFIX_LENGTH = 10
