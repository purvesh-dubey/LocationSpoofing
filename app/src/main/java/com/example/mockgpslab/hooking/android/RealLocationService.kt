package com.example.mockgpslab.hooking.android

import android.annotation.SuppressLint
import android.content.Context
import android.location.LocationManager
import com.example.mockgpslab.data.GeoPoint
import com.example.mockgpslab.hooking.LocationService

/**
 * The "real" service for the demo, backed by Android's LocationManager. It lives in its own sub-package so
 * everything in com.example.mockgpslab.hooking itself stays free of Android imports (and testable on the JVM).
 */
class RealLocationService(context: Context) : LocationService {
    private val locationManager = context.applicationContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    @SuppressLint("MissingPermission") // A missing grant surfaces as SecurityException, handled right here.
    override fun getLastKnownLocation(provider: String): GeoPoint? = try {
        locationManager.getLastKnownLocation(provider)?.let { GeoPoint(it.latitude, it.longitude) }
    } catch (_: SecurityException) {
        null // Permission not granted: report "no location" rather than crashing the demo.
    } catch (_: IllegalArgumentException) {
        null // Unknown provider name.
    }

    override fun isProviderEnabled(provider: String): Boolean = try {
        locationManager.isProviderEnabled(provider)
    } catch (_: IllegalArgumentException) {
        false
    }
}
