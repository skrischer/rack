package de.rack.app.timer

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import de.rack.app.MainActivity
import de.rack.app.R
import de.rack.app.notifications.REST_DONE_VIBRATION
import de.rack.app.notifications.REST_TIMER_CHANNEL_ID
import de.rack.app.ui.timer.RestUiState
import de.rack.app.ui.timer.SessionUiState
import de.rack.app.ui.timer.TimerUiState

/** The ongoing-timer notification id; its channel is created at app start by `NotificationChannels`. */
const val TIMER_NOTIFICATION_ID = 1001

/**
 * Builds the persistent foreground-service notification for the Phase-8 timers (see
 * docs/specs/spec-timers.md) on the shared high-importance `rest_timer` channel that
 * [de.rack.app.notifications.NotificationChannels] creates at app start. That channel
 * carries the vibration + short sound signalling rest completion, so a backgrounded
 * user is alerted through the OS; the ongoing notification shows the live
 * rest-remaining / session-elapsed and the +15 s / -15 s / skip / restart actions that
 * mirror the in-app rest bar. Notification rendering is isolated here to keep
 * [TimerService] focused on lifecycle.
 */
class TimerNotifications(
    private val context: Context,
) {
    private val manager: NotificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    /**
     * The ongoing notification reflecting [state]: title shows session elapsed,
     * text shows the rest remaining (or that no rest is running), with the four rest
     * controls as actions. Silent on update ([NotificationCompat.setOnlyAlertOnce]);
     * the completion alert is delivered separately via [alertRestDone].
     */
    fun ongoing(state: TimerUiState): Notification =
        baseBuilder()
            .setContentTitle(sessionTitle(state.session))
            .setContentText(restText(state.rest))
            .setOnlyAlertOnce(true)
            .addAction(0, context.getString(R.string.timer_action_subtract), actionIntent(ACTION_SUBTRACT))
            .addAction(0, context.getString(R.string.timer_action_add), actionIntent(ACTION_ADD))
            .addAction(0, context.getString(R.string.timer_action_skip), actionIntent(ACTION_SKIP))
            .addAction(0, context.getString(R.string.timer_action_restart), actionIntent(ACTION_RESTART))
            .build()

    /**
     * Re-post the ongoing notification with the high-importance alert flags set so a
     * backgrounded device vibrates and sounds on rest completion. Channel-level
     * vibration/sound carries the alert on Android 8+; the pattern/defaults below
     * cover the immediate heads-up.
     */
    fun alertRestDone(state: TimerUiState) {
        val notification =
            baseBuilder()
                .setContentTitle(context.getString(R.string.timer_rest_done_title))
                .setContentText(restDoneText(state.session))
                .setOnlyAlertOnce(false)
                .setVibrate(REST_DONE_VIBRATION)
                .setDefaults(NotificationCompat.DEFAULT_SOUND)
                .addAction(0, context.getString(R.string.timer_action_restart), actionIntent(ACTION_RESTART))
                .build()
        manager.notify(TIMER_NOTIFICATION_ID, notification)
    }

    private fun baseBuilder(): NotificationCompat.Builder =
        NotificationCompat.Builder(context, REST_TIMER_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setOngoing(true)
            .setContentIntent(contentIntent())
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)

    private fun sessionTitle(session: SessionUiState?): String {
        val elapsed = session?.elapsedSeconds ?: 0
        return context.getString(R.string.timer_session_elapsed, formatClock(elapsed))
    }

    private fun restText(rest: RestUiState?): String =
        when {
            rest == null -> context.getString(R.string.timer_no_rest)
            rest.finished -> context.getString(R.string.timer_rest_done_title)
            else -> context.getString(R.string.timer_rest_remaining, formatClock(rest.remainingSeconds))
        }

    private fun restDoneText(session: SessionUiState?): String =
        context.getString(R.string.timer_session_elapsed, formatClock(session?.elapsedSeconds ?: 0))

    private fun contentIntent(): PendingIntent {
        val intent =
            Intent(context, MainActivity::class.java)
                .setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(context, 0, intent, PENDING_FLAGS)
    }

    private fun actionIntent(action: String): PendingIntent {
        val intent = Intent(context, TimerService::class.java).setAction(action)
        return PendingIntent.getService(context, action.hashCode(), intent, PENDING_FLAGS)
    }

    private companion object {
        const val PENDING_FLAGS = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        const val SECONDS_PER_MINUTE = 60

        fun formatClock(totalSeconds: Int): String {
            val safe = totalSeconds.coerceAtLeast(0)
            return "%d:%02d".format(safe / SECONDS_PER_MINUTE, safe % SECONDS_PER_MINUTE)
        }
    }
}
