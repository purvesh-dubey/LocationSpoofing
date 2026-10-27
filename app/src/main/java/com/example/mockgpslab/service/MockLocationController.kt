package com.example.mockgpslab.service

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.example.mockgpslab.permissions.Permissions

/** The UI's only handle on the service. */
object MockLocationController {
    /**
     * Starts the foreground service, but only once location permission is granted: Android 14+ refuses to start a
     * location-type foreground service without it (SecurityException), and the mock needs it anyway.
     *
     * @return false if the permission is missing and nothing was started.
     */
    fun start(context: Context): Boolean {
        if (!Permissions.read(context).anyLocation) return false
        ContextCompat.startForegroundService(context, Intent(context, MockLocationService::class.java))
        return true
    }

    fun stop(context: Context) {
        context.stopService(Intent(context, MockLocationService::class.java))
    }
}
