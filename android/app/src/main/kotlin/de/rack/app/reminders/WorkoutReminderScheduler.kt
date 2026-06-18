package de.rack.app.reminders

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import de.rack.app.domain.ReminderPreferences
import de.rack.app.domain.ReminderSchedule
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/**
 * Turns the device-local [ReminderPreferences] into the next WorkManager run (#66,
 * docs/specs/spec-push-notifications.md). It enqueues a single delayed [WorkoutReminderWorker]
 * for the next matching weekday/time; the worker reschedules the following occurrence when it
 * fires, so the chain rolls forward with no server round-trip. WorkManager persists the
 * enqueued work across app restart and device reboot, satisfying the survive-reboot acceptance.
 *
 * Disabling the reminder (or clearing every weekday) cancels the unique work. The unique name
 * plus [ExistingWorkPolicy.REPLACE] keeps rescheduling idempotent — re-arming from app start or
 * a preference edit never stacks duplicate runs.
 */
class WorkoutReminderScheduler(
    private val context: Context,
) {
    fun reschedule(prefs: ReminderPreferences) {
        val workManager = WorkManager.getInstance(context)
        val delayMillis = ReminderSchedule.nextTriggerDelayMillis(prefs, ZonedDateTime.now())
        if (delayMillis == null) {
            workManager.cancelUniqueWork(WORK_NAME)
            return
        }
        val request =
            OneTimeWorkRequestBuilder<WorkoutReminderWorker>()
                .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
                .build()
        workManager.enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    companion object {
        /** Unique work name so only one reminder run is ever pending. */
        const val WORK_NAME = "workout_reminder"
    }
}
