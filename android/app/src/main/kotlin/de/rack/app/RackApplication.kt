package de.rack.app

import android.app.Application
import de.rack.app.di.AppContainer
import de.rack.app.notifications.NotificationChannels
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Application entry point that owns the process-wide [AppContainer], so the
 * single SupabaseClient and its repositories are constructed once and shared.
 */
class RackApplication : Application() {
    lateinit var container: AppContainer
        private set

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        NotificationChannels.ensure(this)
        rearmWorkoutReminder()
    }

    /**
     * Re-arm the workout reminder from the persisted prefs on app start (#66). WorkManager
     * already persists enqueued work across restart/reboot, so this is an idempotent safety
     * net (REPLACE on a unique name) that keeps the next run aligned with the saved schedule.
     */
    private fun rearmWorkoutReminder() {
        applicationScope.launch {
            val prefs = container.reminderRepository.preferences.first()
            container.workoutReminderScheduler.reschedule(prefs)
        }
    }
}
