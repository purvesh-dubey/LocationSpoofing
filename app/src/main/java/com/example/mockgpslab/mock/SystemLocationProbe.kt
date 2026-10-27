package com.example.mockgpslab.mock

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.example.mockgpslab.data.GeoPoint
import com.example.mockgpslab.permissions.Permissions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/** What an ordinary app would see when it asks the system for a location. */
sealed interface SystemReading {
    data class Fix(
        val point: GeoPoint,
        val provider: String,
        /** True when the system flags the fix as coming from a mock provider. */
        val isMock: Boolean,
        val accuracyMeters: Float?,
        val ageMillis: Long,
    ) : SystemReading

    data object NoPermission : SystemReading

    /** Providers are off, or none produced a fix within the timeout. */
    data object NoFix : SystemReading
}

/**
 * Asks LocationManager for a location exactly like any third-party app would. If the mock pipeline works,
 * the answer is our fake point, flagged as mock. That makes this the in-app proof of the whole chain.
 */
class SystemLocationProbe(private val context: Context) {
    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    @SuppressLint("MissingPermission") // Guarded by the permission check at the top of read().
    suspend fun read(): SystemReading {
        val permissions = Permissions.read(context)
        if (!permissions.anyLocation) return SystemReading.NoPermission

        // The gps provider needs the precise permission; with approximate-only we can still ask network/fused.
        val candidates = buildList {
            if (permissions.fineLocation) add(LocationManager.GPS_PROVIDER)
            add(LocationManager.NETWORK_PROVIDER)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(LocationManager.FUSED_PROVIDER)
        }
        for (provider in candidates) {
            if (!locationManager.isProviderEnabled(provider)) continue
            val location = currentLocation(provider) ?: continue
            return SystemReading.Fix(
                point = GeoPoint(location.latitude, location.longitude),
                provider = location.provider ?: provider,
                isMock = isMock(location),
                accuracyMeters = if (location.hasAccuracy()) location.accuracy else null,
                ageMillis = (android.os.SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos) / 1_000_000,
            )
        }
        return SystemReading.NoFix
    }

    @SuppressLint("MissingPermission")
    private suspend fun currentLocation(provider: String): Location? = withTimeoutOrNull(CURRENT_LOCATION_TIMEOUT_MS) {
        suspendCancellableCoroutine { continuation ->
            val signal = CancellationSignal()
            continuation.invokeOnCancellation { signal.cancel() }
            try {
                // The compat call uses getCurrentLocation on API 30+ and a single update below that.
                LocationManagerCompat.getCurrentLocation(
                    locationManager, provider, signal, ContextCompat.getMainExecutor(context),
                ) { location -> if (continuation.isActive) continuation.resume(location) }
            } catch (_: SecurityException) {
                if (continuation.isActive) continuation.resume(null)
            }
        }
    }

    // Location.isMock() replaced isFromMockProvider() in API 31.
    @Suppress("DEPRECATION")
    private fun isMock(location: Location) =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) location.isMock else location.isFromMockProvider

    private companion object {
        const val CURRENT_LOCATION_TIMEOUT_MS = 4_000L
    }
}
