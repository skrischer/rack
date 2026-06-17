package de.rack.app.ui.timer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import de.rack.app.R
import de.rack.app.ui.theme.RecompTheme
import de.rack.app.ui.theme.SupersetKind

/**
 * The non-blocking, dismissible timer surface embedded over the Phase-3 log screen
 * (docs/specs/spec-timers.md): a session-elapsed line, an explicit session-end
 * action, the running rest pill with the +15 s / -15 s / skip / restart controls
 * mirroring the notification, and the superset/circuit rotation cue alongside it.
 * When notifications are denied it also renders the in-app rationale this step owns.
 * Purely presentational — it renders [state] and forwards control events through
 * [TimerBarActions]; no timer logic or Supabase access here.
 */
@Composable
fun TimerBar(
    state: TimerUiState,
    notificationsDenied: Boolean,
    actions: TimerBarActions,
    modifier: Modifier = Modifier,
) {
    val session = state.session ?: return
    val colors = RecompTheme.colors
    val spacing = RecompTheme.spacing
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .background(colors.panelElevated)
                .border(spacing.border, colors.line)
                .padding(horizontal = spacing.gutter, vertical = spacing.md),
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        if (notificationsDenied) NotificationDeniedNotice()
        SessionRow(elapsedSeconds = session.elapsedSeconds, onEndSession = actions.onEndSession)
        state.rotation?.let { RotationCue(rotation = it) }
        state.rest?.let { RestPill(rest = it, actions = actions) }
    }
}

@Composable
private fun SessionRow(
    elapsedSeconds: Int,
    onEndSession: () -> Unit,
) {
    val colors = RecompTheme.colors
    val type = RecompTheme.typography
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = "SESSION", style = type.kicker, color = colors.dim)
            Text(text = "  ${formatClock(elapsedSeconds)}", style = type.loadValue, color = colors.volt)
        }
        EndSessionButton(onEndSession = onEndSession)
    }
}

@Composable
private fun EndSessionButton(onEndSession: () -> Unit) {
    val colors = RecompTheme.colors
    val spacing = RecompTheme.spacing
    Text(
        text = stringResource(R.string.timer_end_session),
        style = RecompTheme.typography.label,
        color = colors.dim,
        modifier =
            Modifier
                .border(spacing.border, colors.line, RecompTheme.shapes.sm)
                .clickable(onClick = onEndSession)
                .padding(horizontal = spacing.md, vertical = spacing.xs),
    )
}

@Composable
private fun RotationCue(rotation: RotationUiState) {
    val colors = RecompTheme.colors
    val type = RecompTheme.typography
    val spacing = RecompTheme.spacing
    val label =
        if (rotation.kind == SupersetKind.CIRCUIT) {
            stringResource(R.string.timer_rotation_circuit)
        } else {
            stringResource(R.string.timer_rotation_superset)
        }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(colors.supersetTint, RecompTheme.shapes.sm)
                .padding(horizontal = spacing.md, vertical = spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = type.supersetHeader, color = colors.superset)
        Text(
            text = "  → ${rotation.nextExerciseName}",
            style = type.cue,
            color = colors.txt,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun RestPill(
    rest: RestUiState,
    actions: TimerBarActions,
) {
    val colors = RecompTheme.colors
    val type = RecompTheme.typography
    val spacing = RecompTheme.spacing
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val restColor = if (rest.finished) colors.voltDim else colors.volt
        val restText =
            if (rest.finished) {
                stringResource(R.string.timer_rest_done_title)
            } else {
                stringResource(R.string.timer_rest_remaining, formatClock(rest.remainingSeconds))
            }
        Text(text = restText, style = type.loadValue, color = restColor)
        RestControl(label = stringResource(R.string.timer_action_subtract), onClick = actions.onSubtract)
        RestControl(label = stringResource(R.string.timer_action_add), onClick = actions.onAdd)
        RestControl(label = stringResource(R.string.timer_action_skip), onClick = actions.onSkip)
        RestControl(label = stringResource(R.string.timer_action_restart), onClick = actions.onRestart)
    }
}

@Composable
private fun RestControl(
    label: String,
    onClick: () -> Unit,
) {
    val colors = RecompTheme.colors
    val spacing = RecompTheme.spacing
    Text(
        text = label.uppercase(),
        style = RecompTheme.typography.label,
        color = colors.volt,
        modifier =
            Modifier
                .border(spacing.border, colors.line, RecompTheme.shapes.sm)
                .clickable(onClick = onClick)
                .padding(horizontal = spacing.sm, vertical = spacing.xs),
    )
}

@Composable
private fun NotificationDeniedNotice() {
    val colors = RecompTheme.colors
    val spacing = RecompTheme.spacing
    Text(
        text = stringResource(R.string.timer_notifications_denied_notice),
        style = RecompTheme.typography.caption,
        color = colors.warningText,
        modifier =
            Modifier
                .fillMaxWidth()
                .background(colors.warningBg, RecompTheme.shapes.sm)
                .border(spacing.border, colors.legs, RecompTheme.shapes.sm)
                .padding(horizontal = spacing.md, vertical = spacing.xs),
    )
}

private const val SECONDS_PER_MINUTE = 60

/** "m:ss" clock from [totalSeconds] (clamped non-negative), matching the notification. */
private fun formatClock(totalSeconds: Int): String {
    val safe = totalSeconds.coerceAtLeast(0)
    return "%d:%02d".format(safe / SECONDS_PER_MINUTE, safe % SECONDS_PER_MINUTE)
}
