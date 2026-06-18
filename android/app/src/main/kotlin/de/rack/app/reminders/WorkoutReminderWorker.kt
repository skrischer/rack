package de.rack.app.reminders

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import de.rack.app.MainActivity
import de.rack.app.R
import de.rack.app.RackApplication
import de.rack.app.notifications.WORKOUT_REMINDER_CHANNEL_ID
import kotlinx.coroutines.flow.first

/**
 * The WorkManager job that posts a local workout reminder and arms the next one (#66,
 * docs/specs/spec-push-notifications.md). On fire it reads the current
 * [ReminderPreferences][de.rack.app.domain.ReminderPreferences]; if the reminder is still on
 * with a weekday selected it posts on the `workout_reminder` channel and reschedules the next
 * occurrence, so the daily/weekly chain rolls forward with no server round-trip. Posting is
 * guarded by [NotificationManagerCompat.areNotificationsEnabled], so a denied
 * `POST_NOTIFICATIONS` permission degrades to "no notification shown" rather than a crash.
 */
class WorkoutReminderWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as RackApplication).container
        val prefs = container.reminderRepository.preferences.first()
        if (prefs.enabled && prefs.days.isNotEmpty()) {
            postReminder()
            container.workoutReminderScheduler.reschedule(prefs)
        }
        return Result.success()
    }

    private fun postReminder() {
        val manager = NotificationManagerCompat.from(applicationContext)
        if (!manager.areNotificationsEnabled()) return
        val notification =
            NotificationCompat.Builder(applicationContext, WORKOUT_REMINDER_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle(applicationContext.getString(R.string.reminder_notification_title))
                .setContentText(applicationContext.getString(R.string.reminder_notification_body))
                .setContentIntent(launchIntent())
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun launchIntent(): PendingIntent {
        val intent =
            Intent(applicationContext, MainActivity::class.java)
                .setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(applicationContext, 0, intent, PENDING_FLAGS)
    }

    private companion object {
        const val NOTIFICATION_ID = 1003
        const val PENDING_FLAGS = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    }
}
