package com.example.mockgpslab.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.mockgpslab.AppContainer
import com.example.mockgpslab.MainActivity
import com.example.mockgpslab.MockGpsLabApplication
import com.example.mockgpslab.R
import com.example.mockgpslab.data.GeoPoint
import com.example.mockgpslab.mock.EngineState
import com.example.mockgpslab.mock.LocationManagerBackend
import com.example.mockgpslab.mock.MockLocationEngine
import com.example.mockgpslab.mock.MockProviders
import com.example.mockgpslab.mock.SystemMockClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Hosts the [MockLocationEngine].
 *
 * Why a foreground service: the mock must keep running while the user switches to Maps or the camera. A plain
 * background process would be killed within minutes; a foreground service of type "location" is the sanctioned
 * way to do ongoing location work, and its notification is the user's always-visible off switch.
 */
class MockLocationService : Service() {
    private val container: AppContainer get() = (application as MockGpsLabApplication).container

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var engine: MockLocationEngine
    private lateinit var notificationManager: NotificationManager

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(NotificationManager::class.java)
        createChannel()

        engine = MockLocationEngine(
            backend = LocationManagerBackend(getSystemService(Context.LOCATION_SERVICE) as LocationManager),
            scope = scope,
            providers = MockProviders.forSdk(Build.VERSION.SDK_INT),
            clock = SystemMockClock,
            log = { Log.w(TAG, it) },
        )
        container.session.setServiceRunning(true)

        // Mirror engine state for the UI, keep the notification text current, and bail out on failure.
        scope.launch {
            engine.state.collect { state ->
                container.session.setEngineState(state)
                when (state) {
                    is EngineState.Running -> notificationManager.notify(NOTIFICATION_ID, buildNotification(state))
                    is EngineState.NotMockApp, is EngineState.Error -> stopSelf()
                    EngineState.Idle -> Unit
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            // From the notification's Stop button. stopSelf() leads to onDestroy(), which cleans up.
            stopSelf()
            return START_NOT_STICKY
        }

        // startForegroundService() obliges us to call startForeground() within seconds, so do it first,
        // before any slow work (reading DataStore, talking to the system).
        if (!enterForeground()) {
            stopSelf()
            return START_NOT_STICKY
        }

        scope.launch {
            val settings = container.settings
            engine.start(settings.selectedPoint.first(), settings.accuracyMeters.first())
            // While running, follow whatever the user picks next. The same flow also fires once more with
            // the current value, which is harmless: updateTarget with the same point is a no-op.
            launch { settings.selectedPoint.collect { engine.updateTarget(it) } }
            launch { settings.accuracyMeters.collect { engine.setAccuracy(it) } }
        }
        // NOT_STICKY: if the system kills us, don't silently resurrect a mock nobody is watching.
        return START_NOT_STICKY
    }

    private fun enterForeground(): Boolean {
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (fine != android.content.pm.PackageManager.PERMISSION_GRANTED &&
            coarse != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "Refusing to start: location permission not granted")
            return false
        }
        return try {
            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0
            ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(null), type)
            true
        } catch (e: SecurityException) {
            Log.w(TAG, "System refused the location foreground service", e)
            false
        }
    }

    override fun onDestroy() {
        // The one guaranteed cleanup point: unregister every test provider, then stop the loop's scope.
        val finalState = engine.state.value
        engine.stop()
        scope.cancel()
        // engine.stop() reports Idle, but if we are dying BECAUSE of an error, keep that visible to the UI.
        container.session.setEngineState(
            if (finalState is EngineState.NotMockApp || finalState is EngineState.Error) finalState else EngineState.Idle,
        )
        container.session.setServiceRunning(false)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel() {
        val channel = NotificationChannel(CHANNEL_ID, getString(R.string.notification_channel_name), NotificationManager.IMPORTANCE_LOW)
            .apply { description = getString(R.string.notification_channel_description) }
        notificationManager.createNotificationChannel(channel)
    }

    private fun buildNotification(state: EngineState.Running?): Notification {
        val point = state?.let { GeoPoint(it.latitude, it.longitude) }
        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, MockLocationService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_pin)
            .setContentTitle(if (point != null) getString(R.string.notification_active, point.toString()) else getString(R.string.notification_starting))
            .setContentIntent(openApp)
            .addAction(0, getString(R.string.notification_stop), stop)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    companion object {
        private const val TAG = "MockLocationService"
        private const val CHANNEL_ID = "mock_location"
        private const val NOTIFICATION_ID = 1
        const val ACTION_STOP = "com.example.mockgpslab.action.STOP"
    }
}
