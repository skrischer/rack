package de.rack.app.ui.session

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.Dialog
import de.rack.app.ui.theme.RecompTheme

/**
 * Full-screen guided session player launched from the plan-day view. It renders the
 * load-state wrapper and, for a running session, the focused exercise/set card; on
 * finishing it renders the [SessionSummaryBody] with the per-exercise sets/volume and
 * confirm-save / discard actions (docs/specs/spec-session-player.md). Closing or pressing
 * back asks for confirmation and writes nothing — the only persistence point is the
 * summary confirm. Purely presentational: it observes [state] and [durationSeconds] and
 * forwards events through [actions]; no Supabase access and no stepping/rotation/
 * aggregation logic live here.
 */
@Composable
fun SessionPlayerScreen(
    state: SessionPlayerScreenState,
    durationSeconds: Int,
    actions: SessionPlayerActions,
    modifier: Modifier = Modifier,
) {
    val colors = RecompTheme.colors
    var confirmAbandon by remember { mutableStateOf(false) }
    val requestAbandon = { confirmAbandon = true }
    BackHandler(onBack = requestAbandon)
    Column(modifier = modifier.fillMaxSize().background(colors.bg)) {
        SessionTopBar(onClose = requestAbandon)
        Box(modifier = Modifier.weight(1f)) {
            when (state) {
                is SessionPlayerScreenState.Loading -> CenterSpinner()
                is SessionPlayerScreenState.Error -> ErrorPane(message = state.message, onRetry = actions.onRetry)
                is SessionPlayerScreenState.Content ->
                    SessionBody(
                        session = state.session,
                        durationSeconds = durationSeconds,
                        actions = actions,
                        onDiscard = requestAbandon,
                    )
            }
        }
    }
    if (confirmAbandon) {
        AbandonDialog(
            onConfirm = {
                confirmAbandon = false
                actions.onAbandon()
            },
            onDismiss = { confirmAbandon = false },
        )
    }
}

@Composable
private fun SessionBody(
    session: SessionPlayerUiState,
    durationSeconds: Int,
    actions: SessionPlayerActions,
    onDiscard: () -> Unit,
) {
    val summary = session.summary
    if (session.isFinished && summary != null) {
        SessionSummaryBody(
            summary = summary,
            durationSeconds = durationSeconds,
            saveState = session.saveState,
            onConfirmSave = actions.onConfirmSave,
            onDiscard = onDiscard,
        )
        return
    }
    val focused = session.focused ?: return
    val progress = SessionProgress(done = session.done.size, total = session.done.size + 1 + session.remaining.size)
    SessionFocusCard(
        content =
            SessionFocusContent(
                step = focused,
                entries = session.entriesFor(focused.planExerciseId),
                reference = session.referenceFor(focused.planExerciseId),
                rotationCueName = session.rotationCueName,
                progress = progress,
            ),
        actions = actions,
    )
}

/** Done-vs-total step counts shown as the player's progress label ("3 / 12"). */
data class SessionProgress(
    val done: Int,
    val total: Int,
)

/**
 * The render data the focused-exercise card draws, bundled so the card takes one
 * parameter: the [step] in focus, its working [entries] (per-exercise kg/RIR + per-set
 * reps), the last-logged [reference] line, the "Next: <exercise>" [rotationCueName]
 * (null when there is no hand-off), and the session [progress].
 */
data class SessionFocusContent(
    val step: SessionStep,
    val entries: ExerciseEntries,
    val reference: String?,
    val rotationCueName: String?,
    val progress: SessionProgress,
)

@Composable
private fun SessionTopBar(onClose: () -> Unit) {
    val colors = RecompTheme.colors
    val type = RecompTheme.typography
    val spacing = RecompTheme.spacing
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(colors.bg)
                .padding(horizontal = spacing.gutter, vertical = spacing.md),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = "SESSION", style = type.kicker, color = colors.volt)
        Text(
            text = "CLOSE",
            style = type.label,
            color = colors.dim,
            modifier =
                Modifier
                    .border(spacing.border, colors.line, RecompTheme.shapes.sm)
                    .clickable(onClick = onClose)
                    .padding(horizontal = spacing.lg, vertical = spacing.sm),
        )
    }
}

@Composable
private fun AbandonDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = RecompTheme.colors
    val type = RecompTheme.typography
    val spacing = RecompTheme.spacing
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(colors.panel, RecompTheme.shapes.xl)
                    .border(spacing.border, colors.line, RecompTheme.shapes.xl)
                    .padding(spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            Text(text = "ABANDON SESSION", style = type.kicker, color = colors.legs)
            Text(text = "Ticked sets are discarded and nothing is saved.", style = type.body, color = colors.dim)
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm), modifier = Modifier.fillMaxWidth()) {
                DialogButton(label = "KEEP GOING", filled = true, onClick = onDismiss, modifier = Modifier.weight(1f))
                DialogButton(label = "ABANDON", filled = false, onClick = onConfirm, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun DialogButton(
    label: String,
    filled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = RecompTheme.colors
    val type = RecompTheme.typography
    val spacing = RecompTheme.spacing
    val base =
        if (filled) {
            modifier.background(colors.volt, RecompTheme.shapes.md)
        } else {
            modifier.border(spacing.border, colors.line, RecompTheme.shapes.md)
        }
    Text(
        text = label,
        style = type.label,
        color = if (filled) colors.bg else colors.dim,
        modifier = base.clickable(onClick = onClick).padding(vertical = spacing.md),
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun CenterSpinner() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = RecompTheme.colors.volt)
    }
}

@Composable
private fun ErrorPane(
    message: String,
    onRetry: () -> Unit,
) {
    val colors = RecompTheme.colors
    val type = RecompTheme.typography
    val spacing = RecompTheme.spacing
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = spacing.gutter),
        verticalArrangement = Arrangement.spacedBy(spacing.md, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = message, style = type.body, color = colors.legs)
        Text(
            text = "RETRY",
            style = type.label,
            color = colors.bg,
            modifier =
                Modifier
                    .background(colors.volt, RecompTheme.shapes.md)
                    .clickable(onClick = onRetry)
                    .padding(horizontal = spacing.lg, vertical = spacing.sm),
        )
    }
}
