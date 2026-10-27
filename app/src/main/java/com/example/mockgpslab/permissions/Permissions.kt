package com.example.mockgpslab.permissions

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/** Snapshot of the runtime permissions this app cares about. */
data class PermissionStatus(
    val fineLocation: Boolean,
    val coarseLocation: Boolean,
    val notifications: Boolean,
) {
    /** Any location grant is enough to start the location-type foreground service. */
    val anyLocation: Boolean get() = fineLocation || coarseLocation

    /** The user can choose "approximate" in the system dialog on Android 12+, which grants only coarse. */
    val approximateOnly: Boolean get() = coarseLocation && !fineLocation
}

object Permissions {
    /** POST_NOTIFICATIONS is a runtime permission only from API 33; before that notifications are always allowed. */
    val needsNotificationPermission: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    /** Inlined constant: safe because every use is guarded by [needsNotificationPermission] (API 33+). */
    @SuppressLint("InlinedApi")
    const val NOTIFICATIONS = Manifest.permission.POST_NOTIFICATIONS

    val locationPermissions = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    )

    fun read(context: Context): PermissionStatus = PermissionStatus(
        fineLocation = granted(context, Manifest.permission.ACCESS_FINE_LOCATION),
        coarseLocation = granted(context, Manifest.permission.ACCESS_COARSE_LOCATION),
        notifications = !needsNotificationPermission || granted(context, NOTIFICATIONS),
    )

    private fun granted(context: Context, permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
