package com.example.mockgpslab.hooking

import com.example.mockgpslab.data.GeoPoint

/** Stub standing in for the Android-backed RealLocationService. Counts calls so tests can prove what ran. */
class StubLocationService(
    var lastKnown: GeoPoint? = GeoPoint(1.0, 2.0),
    var gpsEnabled: Boolean = false,
) : LocationService {
    var lastKnownCalls = 0
    var failWith: Throwable? = null

    override fun getLastKnownLocation(provider: String): GeoPoint? {
        lastKnownCalls++
        failWith?.let { throw it }
        return lastKnown
    }

    override fun isProviderEnabled(provider: String) = gpsEnabled
}

/** Minimal interface exercising primitives, void and Object-method behaviour. */
interface Calculator {
    fun add(a: Int, b: Int): Int
    fun isReady(): Boolean
    fun ping()
    fun name(): String
}

class RealCalculator : Calculator {
    var pings = 0
    override fun add(a: Int, b: Int) = a + b
    override fun isReady() = true
    override fun ping() { pings++ }
    override fun name() = "real"
    override fun toString() = "RealCalculator!"
}

/** A concrete class: Proxy must refuse it. */
open class NotAnInterface
