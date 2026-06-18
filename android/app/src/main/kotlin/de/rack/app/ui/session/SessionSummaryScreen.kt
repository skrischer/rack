package de.rack.app.ui.session

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.text.style.TextAlign
import de.rack.app.ui.theme.RecompTheme

/**
 * The finished-session summary: per-exercise sets-done-vs-target and total volume from
 * the [summary], the session [durationSeconds] from the Phase-8 session-duration timer,
 * and the confirm-save / discard actions (docs/specs/spec-session-player.md).
 * [onConfirmSave] persists every logged exercise to `set_logs`; [onDiscard] routes to the
 * abandon confirmation (which writes nothing). Purely presentational — it renders the
 * aggregated state and forwards events.
 */
@Composable
internal fun SessionSummaryBody(
    summary: SessionSummary,
    durationSeconds: Int,
    saveState: SessionSaveState,
    onConfirmSave: () -> Unit,
    onDiscard: () -> Unit,
) {
    val colors = RecompTheme.colors
    val type = RecompTheme.typography
    val spacing = RecompTheme.spacing
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.gutter, vertical = spacing.lg),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        Text(text = "SESSION COMPLETE", style = type.kicker, color = colors.volt)
        SummaryTotals(summary = summary, durationSeconds = durationSeconds)
        if (summary.isEmpty) {
            Text(text = "No sets logged this session.", style = type.body, color = colors.dim)
        } else {
            summary.lines.forEach { line -> SummaryLineRow(line = line) }
        }
        if (saveState == SessionSaveState.ERROR) SaveErrorLine()
        SummaryButtons(
            isEmpty = summary.isEmpty,
            saveState = saveState,
            onConfirmSave = onConfirmSave,
            onDiscard = onDiscard,
        )
    }
}

@Composable
private fun SummaryTotals(
    summary: SessionSummary,
    durationSeconds: Int,
) {
    val colors = RecompTheme.colors
    val spacing = RecompTheme.spacing
    Row(
        modifier = Modifier.fillMaxWidth().background(colors.panel, RecompTheme.shapes.xl).padding(spacing.lg),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        TotalCell(label = "DURATION", value = formatClock(durationSeconds))
        TotalCell(label = "VOLUME", value = "${formatVolume(summary.totalVolume)} kg")
    }
}

@Composable
private fun TotalCell(
    label: String,
    value: String,
) {
    val colors = RecompTheme.colors
    val type = RecompTheme.typography
    val spacing = RecompTheme.spacing
    Column(verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
        Text(text = label, style = type.caption, color = colors.dim)
        Text(text = value, style = type.dayTitle, color = colors.volt)
    }
}

@Composable
private fun SummaryLineRow(line: SessionSummaryLine) {
    val colors = RecompTheme.colors
    val type = RecompTheme.typography
    val spacing = RecompTheme.spacing
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .border(spacing.border, colors.line, RecompTheme.shapes.md)
                .padding(horizontal = spacing.lg, vertical = spacing.md),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = line.name, style = type.exerciseName, color = colors.txt)
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
            Text(text = "${line.setsDone} / ${line.targetSets} sets", style = type.label, color = colors.volt)
            Text(text = "${formatVolume(line.volume)} kg", style = type.lastTime, color = colors.dim)
        }
    }
}

@Composable
private fun SaveErrorLine() {
    Text(
        text = "Could not save the session. Check your connection and try again.",
        style = RecompTheme.typography.lastTime,
        color = RecompTheme.colors.legs,
    )
}

@Composable
private fun SummaryButtons(
    isEmpty: Boolean,
    saveState: SessionSaveState,
    onConfirmSave: () -> Unit,
    onDiscard: () -> Unit,
) {
    val spacing = RecompTheme.spacing
    val saving = saveState == SessionSaveState.SAVING
    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        if (!isEmpty) {
            PrimaryButton(
                label = if (saving) "SAVING…" else "CONFIRM & SAVE",
                enabled = !saving,
                onClick = onConfirmSave,
            )
        }
        SecondaryButton(
            label = if (isEmpty) "CLOSE" else "DISCARD SESSION",
            enabled = !saving,
            onClick = onDiscard,
        )
    }
}

@Composable
private fun PrimaryButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = RecompTheme.colors
    val type = RecompTheme.typography
    val spacing = RecompTheme.spacing
    val fill = if (enabled) colors.volt else colors.voltDim
    Text(
        text = label,
        style = type.label,
        color = colors.bg,
        modifier =
            Modifier
                .fillMaxWidth()
                .background(fill, RecompTheme.shapes.md)
                .clickable(enabled = enabled, onClick = onClick)
                .padding(vertical = spacing.md),
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun SecondaryButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = RecompTheme.colors
    val type = RecompTheme.typography
    val spacing = RecompTheme.spacing
    Text(
        text = label,
        style = type.label,
        color = colors.dim,
        modifier =
            Modifier
                .fillMaxWidth()
                .border(spacing.border, colors.line, RecompTheme.shapes.md)
                .clickable(enabled = enabled, onClick = onClick)
                .padding(vertical = spacing.md),
        textAlign = TextAlign.Center,
    )
}

private const val SECONDS_PER_MINUTE = 60

/** "m:ss" clock from [totalSeconds] (clamped non-negative), matching the timer bar. */
private fun formatClock(totalSeconds: Int): String {
    val safe = totalSeconds.coerceAtLeast(0)
    return "%d:%02d".format(safe / SECONDS_PER_MINUTE, safe % SECONDS_PER_MINUTE)
}

/** Volume without a trailing ".0", mirroring the logged-set weight formatting. */
private fun formatVolume(volume: Double): String =
    if (volume % 1.0 == 0.0) volume.toLong().toString() else volume.toString()
