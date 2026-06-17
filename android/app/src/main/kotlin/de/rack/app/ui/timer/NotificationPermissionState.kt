package de.rack.app.ui.timer

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import de.rack.app.R
import de.rack.app.ui.theme.RecompTheme

/**
 * Owns the Android 13+ `POST_NOTIFICATIONS` runtime-permission flow for the Phase-8
 * timers (this step, #54, owns the flow; the in-app rationale surface is #55 — see
 * docs/specs/spec-timers.md). [rememberNotificationPermission] exposes whether the
 * permission is granted and a [NotificationPermissionState.request] that prompts the
 * user with a rationale; if denied, timers still run and alert by vibration, so the
 * caller never blocks on the result. On API < 33 the permission is implicitly
 * granted and [NotificationPermissionState.request] is a no-op.
 */
class NotificationPermissionState(
    val isGranted: Boolean,
    val request: () -> Unit,
)

/**
 * Returns a [NotificationPermissionState] that, on first call to
 * [NotificationPermissionState.request], shows a rationale dialog and then launches
 * the system `POST_NOTIFICATIONS` prompt. The dialog is rendered inline by this
 * composable. Callers (the log embed, #55) invoke `request()` when the timer first
 * starts; declining does not stop the timer — only the notification surface is lost.
 */
@Composable
fun rememberNotificationPermission(): NotificationPermissionState {
    val context = LocalContext.current
    val required = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    var granted by remember {
        mutableStateOf(!required || hasPostNotifications(context))
    }
    var showRationale by remember { mutableStateOf(false) }

    val launcher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { result -> granted = result }

    if (showRationale) {
        NotificationRationaleDialog(
            onConfirm = {
                showRationale = false
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            },
            onDismiss = { showRationale = false },
        )
    }

    return NotificationPermissionState(
        isGranted = granted,
        request = { if (required && !granted) showRationale = true },
    )
}

private fun hasPostNotifications(context: android.content.Context): Boolean =
    ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.POST_NOTIFICATIONS,
    ) == PackageManager.PERMISSION_GRANTED

@Composable
private fun NotificationRationaleDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = RecompTheme.colors.panel,
        title = { Text(stringResource(R.string.timer_notification_rationale_title)) },
        text = { Text(stringResource(R.string.timer_notification_rationale)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.timer_permission_allow))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.timer_permission_not_now))
            }
        },
    )
}
