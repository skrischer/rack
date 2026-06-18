package de.rack.app.notifications

import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import de.rack.app.R

/**
 * The local rest-timer-done helper (#66, docs/specs/spec-push-notifications.md): a one-shot
 * notification on the high-importance `rest_timer` channel (vibrate + sound carried at the
 * channel level) for the Phase 8/9 timer flows to call when a rest completes. This phase wires
 * the channel and helper, not the timer logic — the channel is created by
 * [NotificationChannels]; the foreground-service path keeps its own ongoing notification.
 *
 * Posting is guarded by [NotificationManagerCompat.areNotificationsEnabled] so a denied
 * `POST_NOTIFICATIONS` permission degrades to "no notification shown" rather than a crash.
 */
object RestTimerNotifier {
    /** A distinct id from the ongoing timer notification (1001), so this alert posts alongside it. */
    private const val NOTIFICATION_ID = 1002

    fun showRestTimerDone(context: Context) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        val notification =
            NotificationCompat.Builder(context, REST_TIMER_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle(context.getString(R.string.timer_rest_done_title))
                .setContentText(context.getString(R.string.rest_timer_done_body))
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setVibrate(REST_DONE_VIBRATION)
                .setDefaults(NotificationCompat.DEFAULT_SOUND)
                .build()
        manager.notify(NOTIFICATION_ID, notification)
    }
}
