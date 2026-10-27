package com.example.mockgpslab.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings

object DeveloperOptions {
    /** True once the user has unlocked Developer Options (tapped "Build number" seven times). Public setting. */
    fun isEnabled(context: Context): Boolean =
        Settings.Global.getInt(context.contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1

    /**
     * Opens Developer Options, or the top-level Settings app if this device has no screen for it
     * (which also happens when Developer Options has never been unlocked on some builds).
     *
     * We try-and-catch rather than resolveActivity(): since Android 11, package visibility rules make
     * resolveActivity() return null for intents the system could perfectly well handle.
     */
    fun open(context: Context) {
        try {
            context.startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).newTask())
        } catch (_: ActivityNotFoundException) {
            context.startActivity(Intent(Settings.ACTION_SETTINGS).newTask())
        }
    }

    private fun Intent.newTask() = addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
