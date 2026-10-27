@file:Suppress("DEPRECATION") // Criteria is only used for the pre-API-31 overload

package com.example.mockgpslab.mock

import android.annotation.SuppressLint
import android.location.Criteria
import android.location.Location
import android.location.LocationManager
import android.location.provider.ProviderProperties
import android.os.Build
import android.os.SystemClock
import androidx.annotation.RequiresApi

/**
 * The real thing: forwards to LocationManager's public test-provider API.
 * Everything here is documented SDK surface; no reflection and no hidden APIs.
 */
class LocationManagerBackend(private val locationManager: LocationManager) : MockProviderBackend {

    override fun addTestProvider(provider: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) addModern(provider) else addLegacy(provider)
    }

    /** API 31+: the provider's capabilities are described by a ProviderProperties object. */
    @RequiresApi(Build.VERSION_CODES.S)
    private fun addModern(provider: String) {
        val properties = ProviderProperties.Builder()
            .setHasNetworkRequirement(provider == LocationManager.NETWORK_PROVIDER)
            .setHasSatelliteRequirement(provider == LocationManager.GPS_PROVIDER)
            .setHasCellRequirement(false)
            .setHasMonetaryCost(false)
            .setHasAltitudeSupport(true)
            .setHasSpeedSupport(true)
            .setHasBearingSupport(true)
            .setPowerUsage(ProviderProperties.POWER_USAGE_LOW)
            .setAccuracy(
                if (provider == LocationManager.NETWORK_PROVIDER) ProviderProperties.ACCURACY_COARSE
                else ProviderProperties.ACCURACY_FINE,
            )
            .build()
        locationManager.addTestProvider(provider, properties)
    }

    /**
     * API 26-30: the same information as a long list of booleans.
     *
     * Lint wants ProviderProperties constants for the last two arguments, but that class only exists from API 31.
     * Criteria.POWER_LOW / ACCURACY_FINE / ACCURACY_COARSE have the same numeric values, so they are correct here.
     */
    @SuppressLint("WrongConstant")
    private fun addLegacy(provider: String) {
        val isNetwork = provider == LocationManager.NETWORK_PROVIDER
        locationManager.addTestProvider(
            provider,
            /* requiresNetwork = */ isNetwork,
            /* requiresSatellite = */ provider == LocationManager.GPS_PROVIDER,
            /* requiresCell = */ false,
            /* hasMonetaryCost = */ false,
            /* supportsAltitude = */ true,
            /* supportsSpeed = */ true,
            /* supportsBearing = */ true,
            Criteria.POWER_LOW,
            if (isNetwork) Criteria.ACCURACY_COARSE else Criteria.ACCURACY_FINE,
        )
    }

    override fun setTestProviderEnabled(provider: String, enabled: Boolean) =
        locationManager.setTestProviderEnabled(provider, enabled)

    override fun setTestProviderLocation(provider: String, fix: MockFix) {
        val location = Location(provider).apply {
            latitude = fix.latitude
            longitude = fix.longitude
            accuracy = fix.accuracyMeters
            altitude = fix.altitudeMeters
            speed = fix.speedMetersPerSecond
            bearing = fix.bearingDegrees
            // Both timestamps are required: API 31+ rejects "incomplete" mock locations, and consumers use
            // elapsedRealtimeNanos to judge how fresh a fix is.
            time = fix.timeMillis
            elapsedRealtimeNanos = fix.elapsedRealtimeNanos
        }
        locationManager.setTestProviderLocation(provider, location)
    }

    override fun removeTestProvider(provider: String) = locationManager.removeTestProvider(provider)
}

/** Production clock: wall time for Location.time, boot-relative monotonic time for elapsedRealtimeNanos. */
object SystemMockClock : MockClock {
    override fun currentTimeMillis() = System.currentTimeMillis()
    override fun elapsedRealtimeNanos() = SystemClock.elapsedRealtimeNanos()
}
