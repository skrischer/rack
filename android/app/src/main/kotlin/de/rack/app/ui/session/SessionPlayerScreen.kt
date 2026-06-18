package de.rack.app.ui.session

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import de.rack.app.ui.theme.RecompError
import de.rack.app.ui.theme.RecompLoading
import de.rack.app.ui.theme.RecompTheme
import de.rack.app.ui.theme.recompClick
import de.rack.app.ui.theme.recompPress

/**
 * Full-screen guided session player launched from the plan-day view. It renders the
 * load-state wrapper and, for a running session, the Recomp set-table view (one card per
 * exercise with a previous column and per-set check); on finishing it renders the
 * [SessionSummaryBody] with the per-exercise sets/volume and confirm-save / discard
 * actions (docs/specs/spec-session-player.md). Closing or pressing back asks for
 * confirmation and writes nothing — the only persistence point is the summary confirm.
 * Purely presentational: it observes [state] and [durationSeconds] and forwards events
 * through [actions]; no Supabase access and no aggregation logic live here.
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
    val running = (state as? SessionPlayerScreenState.Content)?.session?.finished == false
    Column(modifier = modifier.fillMaxSize().background(colors.bg)) {
        SessionTopBar(onClose = requestAbandon, onFinish = actions.onFinish, showFinish = running)
        Box(modifier = Modifier.weight(1f)) {
            when (state) {
                is SessionPlayerScreenState.Loading -> RecompLoading()
                is SessionPlayerScreenState.Error -> RecompError(message = state.message, onRetry = actions.onRetry)
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
    if (session.finished && summary != null) {
        SessionSummaryBody(
            summary = summary,
            durationSeconds = durationSeconds,
            saveState = session.saveState,
            onConfirmSave = actions.onConfirmSave,
            onDiscard = onDiscard,
        )
        return
    }
    SessionSetTableBody(
        content = SessionRunningContent(session = session, durationSeconds = durationSeconds),
        actions = actions,
    )
}

@Composable
private fun SessionTopBar(
    onClose: () -> Unit,
    onFinish: () -> Unit,
    showFinish: Boolean,
) {
    val colors = RecompTheme.colors
    val type = RecompTheme.typography
    val spacing = RecompTheme.spacing
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(colors.bg)
                .padding(horizontal = spacing.gutter, vertical = spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "SCHLIESSEN",
            style = type.label,
            color = colors.dim,
            modifier =
                Modifier
                    .border(spacing.border, colors.line, RecompTheme.shapes.sm)
                    .recompPress(onClick = onClose)
                    .padding(horizontal = spacing.lg, vertical = spacing.sm),
        )
        Text(
            text = "SESSION",
            style = type.kicker,
            color = colors.volt,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        if (showFinish) {
            Text(
                text = "BEENDEN",
                style = type.label,
                color = colors.bg,
                modifier =
                    Modifier
                        .background(colors.volt, RecompTheme.shapes.sm)
                        .recompPress(onClick = onFinish)
                        .padding(horizontal = spacing.lg, vertical = spacing.sm),
            )
        } else {
            Spacer(Modifier.width(spacing.huge))
        }
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
            Text(text = "SESSION VERWERFEN", style = type.kicker, color = colors.legs)
            Text(
                text = "Abgehakte Sätze werden verworfen und nichts wird gespeichert.",
                style = type.body,
                color = colors.dim,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm), modifier = Modifier.fillMaxWidth()) {
                DialogButton(label = "WEITER", filled = true, onClick = onDismiss, modifier = Modifier.weight(1f))
                DialogButton(label = "VERWERFEN", filled = false, onClick = onConfirm, modifier = Modifier.weight(1f))
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
        modifier = base.recompClick(onClick = onClick).padding(vertical = spacing.md),
        textAlign = TextAlign.Center,
    )
}
