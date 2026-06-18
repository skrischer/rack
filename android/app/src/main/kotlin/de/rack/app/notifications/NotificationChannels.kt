package de.rack.app.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import de.rack.app.R

/** Stable id of the channel for backgrounded agent-edit pushes (#64 reuses this). */
const val AGENT_UPDATE_CHANNEL_ID = "agent_update"

/** Stable id of the channel for local WorkManager workout reminders (#66 reuses this). */
const val WORKOUT_REMINDER_CHANNEL_ID = "workout_reminder"

/** Stable id of the high-importance channel for the rest/session timer (Phase 8 posts here). */
const val REST_TIMER_CHANNEL_ID = "rest_timer"

/** Vibration pattern for the rest-completion alert; also set per-notification on pre-channel posts. */
val REST_DONE_VIBRATION = longArrayOf(0L, 400L, 200L, 400L)

/**
 * Creates the three local notification channels the app posts on — `agent_update`
 * (backgrounded agent-edit pushes), `workout_reminder` (local WorkManager reminders),
 * and `rest_timer` (the Phase-8 rest/session timer alert) — wired independently of any
 * Firebase / `google-services.json`, so local notifications work on a Play-services-less
 * image (see docs/specs/spec-push-notifications.md). Called once from
 * [de.rack.app.RackApplication.onCreate]; `createNotificationChannel` is idempotent and
 * minSdk is 26, so channels always exist before anything posts.
 */
object NotificationChannels {
    fun ensure(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(agentUpdateChannel(context))
        manager.createNotificationChannel(workoutReminderChannel(context))
        manager.createNotificationChannel(restTimerChannel(context))
    }

    private fun agentUpdateChannel(context: Context): NotificationChannel =
        NotificationChannel(
            AGENT_UPDATE_CHANNEL_ID,
            context.getString(R.string.channel_agent_update_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = context.getString(R.string.channel_agent_update_description) }

    private fun workoutReminderChannel(context: Context): NotificationChannel =
        NotificationChannel(
            WORKOUT_REMINDER_CHANNEL_ID,
            context.getString(R.string.channel_workout_reminder_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = context.getString(R.string.channel_workout_reminder_description) }

    private fun restTimerChannel(context: Context): NotificationChannel =
        NotificationChannel(
            REST_TIMER_CHANNEL_ID,
            context.getString(R.string.timer_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.timer_channel_description)
            enableVibration(true)
            vibrationPattern = REST_DONE_VIBRATION
            val audio =
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION), audio)
        }
}
