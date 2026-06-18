package de.rack.app.ui.session

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import de.rack.app.ui.theme.RecompDivider
import de.rack.app.ui.theme.RecompSetRow
import de.rack.app.ui.theme.RecompSetTable
import de.rack.app.ui.theme.RecompSetTableCallbacks
import de.rack.app.ui.theme.RecompStat
import de.rack.app.ui.theme.RecompStatStrip
import de.rack.app.ui.theme.RecompTheme
import de.rack.app.ui.theme.SupersetKind

/**
 * The active session, logged via the Recomp set table (`Satz | Vorher | kg | Wdh | RIR |
 * ✓`): a live stat strip (Dauer / Volumen / Sätze) over one card per exercise, each a
 * [RecompSetTable] whose previous column shows the last session's matching set and whose
 * per-set check ticks the set (persisting it and auto-starting the rest timer via the
 * ViewModel). Purely presentational — it renders [content] and forwards events through
 * [actions]; no logging, timer, or aggregation logic lives here.
 */
@Composable
fun SessionSetTableBody(
    content: SessionRunningContent,
    actions: SessionPlayerActions,
) {
    val spacing = RecompTheme.spacing
    val session = content.session
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.gutter, vertical = spacing.lg),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        SessionStatStrip(session = session, durationSeconds = content.durationSeconds)
        session.blocks.forEach { block ->
            if (block.groupStart) SupersetHeader(kind = block.kind)
            SessionExerciseCard(block = block, session = session, actions = actions)
        }
    }
}

@Composable
private fun SessionStatStrip(
    session: SessionPlayerUiState,
    durationSeconds: Int,
) {
    RecompStatStrip(
        stats =
            listOf(
                RecompStat(value = formatSessionClock(durationSeconds), label = "Dauer"),
                RecompStat(value = formatSessionMetric(session.liveVolume()), label = "Volumen kg"),
                RecompStat(value = "${session.done.size} / ${session.totalSets}", label = "Sätze"),
            ),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun SupersetHeader(kind: SupersetKind) {
    val colors = RecompTheme.colors
    val spacing = RecompTheme.spacing
    val label = if (kind == SupersetKind.CIRCUIT) "ZIRKEL" else "SUPERSATZ"
    Text(
        text = label,
        style = RecompTheme.typography.supersetHeader,
        color = colors.superset,
        modifier = Modifier.padding(start = spacing.xs, top = spacing.xs),
    )
}

@Composable
private fun SessionExerciseCard(
    block: SessionExerciseBlock,
    session: SessionPlayerUiState,
    actions: SessionPlayerActions,
) {
    val colors = RecompTheme.colors
    val spacing = RecompTheme.spacing
    val entries = session.entriesFor(block.planExerciseId)
    val previous = session.previousFor(block.planExerciseId)
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(colors.panel, RecompTheme.shapes.xl)
                .border(spacing.border, colors.line, RecompTheme.shapes.xl),
    ) {
        ExerciseCardHead(block = block)
        RecompDivider()
        RecompSetTable(
            rows = setRows(block = block, entries = entries, previous = previous, session = session),
            callbacks = tableCallbacks(planExerciseId = block.planExerciseId, actions = actions),
            modifier = Modifier.padding(horizontal = spacing.lg, vertical = spacing.xs),
        )
    }
}

@Composable
private fun ExerciseCardHead(block: SessionExerciseBlock) {
    val colors = RecompTheme.colors
    val type = RecompTheme.typography
    val spacing = RecompTheme.spacing
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(colors.panelElevated)
                .padding(horizontal = spacing.lg, vertical = spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = block.name, style = type.exerciseName, color = colors.txt, modifier = Modifier.weight(1f))
        block.targetLabel()?.let { label ->
            Text(text = "  $label", style = type.loadValue, color = colors.dim)
        }
    }
}

/** "4 × 5-8 · RIR 1" from the block's plan target and target RIR, or null when both absent. */
private fun SessionExerciseBlock.targetLabel(): String? {
    val parts =
        buildList {
            target?.takeIf { it.isNotBlank() }?.let { add(it) }
            rir?.let { add("RIR $it") }
        }
    return parts.joinToString(separator = " · ").ifBlank { null }
}

private fun setRows(
    block: SessionExerciseBlock,
    entries: ExerciseEntries,
    previous: List<String>,
    session: SessionPlayerUiState,
): List<RecompSetRow> =
    (0 until block.setCount).map { index ->
        RecompSetRow(
            setLabel = (index + 1).toString(),
            weight = entries.weight,
            reps = entries.reps[index].orEmpty(),
            rir = entries.rir,
            previous = previous.getOrNull(index)?.takeIf { it.isNotBlank() },
            done = session.isSetDone(block.planExerciseId, index),
        )
    }

private fun tableCallbacks(
    planExerciseId: String,
    actions: SessionPlayerActions,
): RecompSetTableCallbacks =
    RecompSetTableCallbacks(
        onWeightChange = { _, value -> actions.onWeightChange(planExerciseId, value) },
        onRepsChange = { index, value -> actions.onRepsChange(planExerciseId, index, value) },
        onRirChange = { _, value -> actions.onRirChange(planExerciseId, value) },
        onToggleDone = { index -> actions.onToggleSet(planExerciseId, index) },
    )
