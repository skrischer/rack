package de.rack.app.ui.session

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import de.rack.app.ui.theme.RecompTheme

/**
 * Full-screen guided session player launched from the plan-day view. It renders the
 * load-state wrapper and, for a running session, the focused exercise/set card with a
 * one-tap set tick over editable per-set reps and a single per-exercise kg and RIR
 * (docs/specs/spec-session-player.md). Purely presentational — it observes [state] and
 * forwards edit / tick / retry / close events through [actions]; no Supabase access and
 * no stepping/rotation logic live here. The summary and confirm-save arrive in #60.
 */
@Composable
fun SessionPlayerScreen(
    state: SessionPlayerScreenState,
    actions: SessionPlayerActions,
    modifier: Modifier = Modifier,
) {
    val colors = RecompTheme.colors
    Column(modifier = modifier.fillMaxSize().background(colors.bg)) {
        SessionTopBar(onClose = actions.onClose)
        Box(modifier = Modifier.weight(1f)) {
            when (state) {
                is SessionPlayerScreenState.Loading -> CenterSpinner()
                is SessionPlayerScreenState.Error -> ErrorPane(message = state.message, onRetry = actions.onRetry)
                is SessionPlayerScreenState.Content -> SessionBody(session = state.session, actions = actions)
            }
        }
    }
}

@Composable
private fun SessionBody(
    session: SessionPlayerUiState,
    actions: SessionPlayerActions,
) {
    val focused = session.focused
    if (focused == null) {
        SessionComplete(done = session.done.size)
        return
    }
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
private fun SessionComplete(done: Int) {
    val colors = RecompTheme.colors
    val type = RecompTheme.typography
    val spacing = RecompTheme.spacing
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = spacing.gutter),
        verticalArrangement = Arrangement.spacedBy(spacing.sm, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = "SESSION COMPLETE", style = type.kicker, color = colors.volt)
        Text(text = "$done sets ticked", style = type.body, color = colors.dim)
    }
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
