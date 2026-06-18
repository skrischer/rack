package de.rack.app.ui.session

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import de.rack.app.ui.theme.RecompDivider
import de.rack.app.ui.theme.RecompEmpty
import de.rack.app.ui.theme.RecompGhostButton
import de.rack.app.ui.theme.RecompPrimaryButton
import de.rack.app.ui.theme.RecompStat
import de.rack.app.ui.theme.RecompStatStrip
import de.rack.app.ui.theme.RecompTheme

/**
 * The finished-session summary: a Recomp stat strip (Dauer / Volumen / Sätze) over a card
 * of per-exercise sets-done-vs-target and volume rows from the [summary], with the session
 * [durationSeconds] from the Phase-8 session-duration timer and the confirm-save / discard
 * actions (docs/specs/spec-session-player.md). [onConfirmSave] persists every logged
 * exercise to `set_logs`; [onDiscard] routes to the abandon confirmation (which writes
 * nothing). Purely presentational — it renders the aggregated state and forwards events.
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
        SummaryStatStrip(summary = summary, durationSeconds = durationSeconds)
        if (summary.isEmpty) {
            RecompEmpty(text = "Keine Sätze protokolliert.")
        } else {
            Text(text = "ÜBUNGEN", style = type.label, color = colors.dim)
            SummaryExerciseCard(lines = summary.lines)
        }
        if (saveState == SessionSaveState.ERROR) SaveErrorLine()
        SummaryActions(
            isEmpty = summary.isEmpty,
            saveState = saveState,
            onConfirmSave = onConfirmSave,
            onDiscard = onDiscard,
        )
    }
}

@Composable
private fun SummaryStatStrip(
    summary: SessionSummary,
    durationSeconds: Int,
) {
    RecompStatStrip(
        stats =
            listOf(
                RecompStat(value = formatSessionClock(durationSeconds), label = "Dauer"),
                RecompStat(value = formatSessionMetric(summary.totalVolume), label = "Volumen kg"),
                RecompStat(value = summary.lines.sumOf(SessionSummaryLine::setsDone).toString(), label = "Sätze"),
            ),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun SummaryExerciseCard(lines: List<SessionSummaryLine>) {
    val colors = RecompTheme.colors
    val spacing = RecompTheme.spacing
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(colors.panel, RecompTheme.shapes.xl)
                .border(spacing.border, colors.line, RecompTheme.shapes.xl),
    ) {
        lines.forEachIndexed { index, line ->
            if (index > 0) RecompDivider()
            SummaryLineRow(line = line)
        }
    }
}

@Composable
private fun SummaryLineRow(line: SessionSummaryLine) {
    val colors = RecompTheme.colors
    val type = RecompTheme.typography
    val spacing = RecompTheme.spacing
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.cardInsetH, vertical = spacing.rowInsetV),
        horizontalArrangement = Arrangement.spacedBy(spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = line.name, style = type.exerciseName, color = colors.txt, modifier = Modifier.weight(1f))
        Text(
            text = "${line.setsDone}/${line.targetSets} · ${formatSessionMetric(line.volume)} kg",
            style = type.loadValue,
            color = colors.dim,
        )
    }
}

@Composable
private fun SaveErrorLine() {
    Text(
        text = "Session konnte nicht gespeichert werden. Verbindung prüfen und erneut versuchen.",
        style = RecompTheme.typography.lastTime,
        color = RecompTheme.colors.legs,
    )
}

@Composable
private fun SummaryActions(
    isEmpty: Boolean,
    saveState: SessionSaveState,
    onConfirmSave: () -> Unit,
    onDiscard: () -> Unit,
) {
    val spacing = RecompTheme.spacing
    val saving = saveState == SessionSaveState.SAVING
    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        if (!isEmpty) {
            RecompPrimaryButton(
                text = if (saving) "Speichert…" else "Speichern",
                onClick = onConfirmSave,
                enabled = !saving,
                fillMaxWidth = true,
            )
        }
        RecompGhostButton(
            text = if (isEmpty) "Schließen" else "Verwerfen",
            onClick = onDiscard,
            enabled = !saving,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
