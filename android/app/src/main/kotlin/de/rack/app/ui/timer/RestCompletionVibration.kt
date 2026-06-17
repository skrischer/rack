package de.rack.app.ui.timer

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.flow.Flow

/** The rest-completion vibration pattern, mirroring the notification channel pattern. */
private val REST_DONE_PATTERN = longArrayOf(0L, 400L, 200L, 400L)

/**
 * The in-app completion alert this step owns (docs/specs/spec-timers.md): when the
 * foregrounded user's rest crosses zero, vibrate even if `POST_NOTIFICATIONS` was
 * denied, so the "done" signal never depends on the notification. Subscribes to
 * [restFinished] for the lifetime of the composition; the in-app bar's finished state
 * carries the visual half of the same alert.
 */
@Composable
fun RestCompletionVibration(restFinished: Flow<Unit>) {
    val context = LocalContext.current
    LaunchedEffect(restFinished) {
        restFinished.collect { vibrateRestDone(context) }
    }
}

private fun vibrateRestDone(context: Context) {
    val vibrator = vibratorOf(context) ?: return
    if (!vibrator.hasVibrator()) return
    vibrator.vibrate(VibrationEffect.createWaveform(REST_DONE_PATTERN, -1))
}

private fun vibratorOf(context: Context): Vibrator? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val manager = context.getSystemService(VibratorManager::class.java)
        manager?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }
