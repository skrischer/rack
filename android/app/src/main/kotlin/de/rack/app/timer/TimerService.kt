package de.rack.app.timer

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import de.rack.app.RackApplication
import de.rack.app.data.TimerController
import de.rack.app.ui.timer.TimerUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/** Notification action names mirroring the in-app rest controls and the session end. */
const val ACTION_ADD = "de.rack.app.timer.ADD"
const val ACTION_SUBTRACT = "de.rack.app.timer.SUBTRACT"
const val ACTION_SKIP = "de.rack.app.timer.SKIP"
const val ACTION_RESTART = "de.rack.app.timer.RESTART"
const val ACTION_END_SESSION = "de.rack.app.timer.END_SESSION"

/**
 * The session-scoped started foreground service that hosts the Phase-8 timers (see
 * docs/specs/spec-timers.md "Service lifecycle"). It runs from the first logged set
 * to the explicit session end; the running session timer counts as an active timer,
 * so the service and its persistent notification stay up between rests, when no rest
 * is running, and across backgrounding — there is no auto-idle stop path. The engine
 * state lives in the process-wide [TimerController]; this service only anchors it in
 * the foreground, mirrors it into the persistent notification, fires the
 * vibrate + sound on rest completion, and routes the notification actions, which
 * behave identically to the in-app controls. The service stops in exactly one place:
 * the explicit [ACTION_END_SESSION] (or [TimerController.isSessionActive] going
 * false), which clears the notification.
 */
class TimerService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var controller: TimerController
    private lateinit var notifications: TimerNotifications

    override fun onCreate() {
        super.onCreate()
        controller = (application as RackApplication).container.timerController
        notifications = TimerNotifications(this)
        notifications.ensureChannel()
        startInForeground()
        observeController()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        when (intent?.action) {
            ACTION_ADD -> controller.addRest()
            ACTION_SUBTRACT -> controller.subtractRest()
            ACTION_SKIP -> controller.skipRest()
            ACTION_RESTART -> controller.restartRest()
            ACTION_END_SESSION -> controller.stopSession()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun startInForeground() {
        val notification = notifications.ongoing(controller.uiState.value)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceCompat.startForeground(
                this,
                TIMER_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            ServiceCompat.startForeground(this, TIMER_NOTIFICATION_ID, notification, 0)
        }
    }

    private fun observeController() {
        controller.uiState
            .onEach(::reNotify)
            .launchIn(scope)
        controller.restFinished
            .onEach { notifications.alertRestDone(controller.uiState.value) }
            .launchIn(scope)
        controller.isSessionActive
            .onEach { active -> if (!active) stopSelfAndClear() }
            .launchIn(scope)
    }

    private fun reNotify(state: TimerUiState) {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(TIMER_NOTIFICATION_ID, notifications.ongoing(state))
    }

    private fun stopSelfAndClear() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    companion object {
        /** Start the foreground service hosting the session (called on session start). */
        fun start(context: Context) {
            val intent = Intent(context, TimerService::class.java)
            context.startForegroundService(intent)
        }

        /** Stop the service and clear its notification (called on explicit session end). */
        fun stop(context: Context) {
            val intent = Intent(context, TimerService::class.java).setAction(ACTION_END_SESSION)
            context.startService(intent)
        }
    }
}
