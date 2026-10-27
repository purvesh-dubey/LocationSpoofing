package com.example.mockgpslab.permissions

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * Tracks permission state and launches the system dialogs.
 *
 * State is re-read on every ON_RESUME because the user can flip permissions in system Settings
 * and come back; the result callbacks alone would miss that.
 */
class PermissionController(
    val status: PermissionStatus,
    val requestLocation: () -> Unit,
    val requestNotifications: () -> Unit,
    val openAppSettings: () -> Unit,
    /** True once we have asked and Android will no longer show the dialog, so only Settings can help. */
    val locationPermanentlyDenied: Boolean,
    val notificationsPermanentlyDenied: Boolean,
)

@Composable
fun rememberPermissionController(): PermissionController {
    val context = LocalContext.current
    var status by remember { mutableStateOf(Permissions.read(context)) }
    var askedLocation by remember { mutableStateOf(false) }
    var askedNotifications by remember { mutableStateOf(false) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) status = Permissions.read(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val locationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        status = Permissions.read(context)
    }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        status = Permissions.read(context)
    }

    val activity = context.findActivity()
    // shouldShowRequestPermissionRationale is false both "before the first ask" and "after Don't ask again",
    // so we only trust it after we have asked at least once in this process.
    val locationPermanentlyDenied = askedLocation && !status.anyLocation && activity != null &&
        Permissions.locationPermissions.none { activity.shouldShowRequestPermissionRationale(it) }
    val notificationsPermanentlyDenied = askedNotifications && !status.notifications && activity != null &&
        Permissions.needsNotificationPermission &&
        !activity.shouldShowRequestPermissionRationale(Permissions.NOTIFICATIONS)

    return PermissionController(
        status = status,
        requestLocation = {
            askedLocation = true
            locationLauncher.launch(Permissions.locationPermissions)
        },
        requestNotifications = {
            askedNotifications = true
            notificationLauncher.launch(Permissions.NOTIFICATIONS)
        },
        openAppSettings = { context.startActivity(appSettingsIntent(context)) },
        locationPermanentlyDenied = locationPermanentlyDenied,
        notificationsPermanentlyDenied = notificationsPermanentlyDenied,
    )
}

private fun appSettingsIntent(context: Context) =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

private fun Context.findActivity(): Activity? {
    var c = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

/** Explains WHY each permission is needed before asking, then offers the right action for the current state. */
@Composable
fun PermissionsCard(controller: PermissionController, modifier: Modifier = Modifier) {
    val s = controller.status
    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Permissions", style = MaterialTheme.typography.titleMedium)

            PermissionRow(
                title = "Location",
                granted = s.anyLocation,
                detail = when {
                    s.fineLocation -> "Granted (precise)."
                    s.approximateOnly -> "Only approximate location granted. Choose \"Precise\" so the in-app " +
                        "\"what the system reports\" check can read the GPS provider."
                    else -> "Needed for two things: Android only lets a location-type foreground service run " +
                        "while the app holds location permission, and the app reads back what the system " +
                        "reports so you can verify the mock works. Your real location is never uploaded."
                },
                actionLabel = if (controller.locationPermanentlyDenied) "Open app settings" else "Grant location",
                onAction = if (controller.locationPermanentlyDenied) controller.openAppSettings else controller.requestLocation,
                showAction = !s.fineLocation,
            )

            if (Permissions.needsNotificationPermission) {
                PermissionRow(
                    title = "Notifications",
                    granted = s.notifications,
                    detail = if (s.notifications) "Granted." else
                        "Needed to show the ongoing \"Mock location active\" notification with its Stop button. " +
                            "Without it the mock still runs, but you won't see or be able to stop it from the shade.",
                    actionLabel = if (controller.notificationsPermanentlyDenied) "Open app settings" else "Allow notifications",
                    onAction = if (controller.notificationsPermanentlyDenied) controller.openAppSettings else controller.requestNotifications,
                    showAction = !s.notifications,
                )
            }
        }
    }
}

@Composable
private fun PermissionRow(
    title: String,
    granted: Boolean,
    detail: String,
    actionLabel: String,
    onAction: () -> Unit,
    showAction: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (granted) "✓" else "✗", color = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
            Text(title, style = MaterialTheme.typography.titleSmall)
        }
        Text(detail, style = MaterialTheme.typography.bodySmall)
        if (showAction) {
            if (granted) OutlinedButton(onClick = onAction) { Text(actionLabel) } else Button(onClick = onAction) { Text(actionLabel) }
        }
    }
}
