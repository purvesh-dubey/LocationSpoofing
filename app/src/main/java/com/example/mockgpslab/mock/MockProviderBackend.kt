package com.example.mockgpslab.mock

/**
 * One fake GPS fix. Deliberately NOT an android.location.Location: the engine only deals in plain data,
 * and the real backend turns it into a Location. That is what lets the engine be unit tested on the JVM.
 */
data class MockFix(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val altitudeMeters: Double,
    val speedMetersPerSecond: Float,
    val bearingDegrees: Float,
    val timeMillis: Long,
    val elapsedRealtimeNanos: Long,
)

/** Provider names are the same strings as LocationManager.GPS_PROVIDER etc., kept here to avoid an Android import. */
object MockProviders {
    const val GPS = "gps"
    const val NETWORK = "network"
    const val FUSED = "fused"

    /** FUSED_PROVIDER only exists from API 31 (Android 12). */
    fun forSdk(sdkInt: Int): List<MockProviderSpec> = buildList {
        add(MockProviderSpec(GPS, optional = false))
        add(MockProviderSpec(NETWORK, optional = false))
        if (sdkInt >= 31) add(MockProviderSpec(FUSED, optional = true))
    }
}

/**
 * @param optional if the platform rejects an optional provider we log and carry on; a required one is fatal.
 */
data class MockProviderSpec(val name: String, val optional: Boolean)

/**
 * The slice of LocationManager's test-provider API that the engine needs.
 *
 * Contract (mirrors the real LocationManager so a fake can behave the same way):
 *  - every method throws [SecurityException] when this app is not the selected mock location app;
 *  - [addTestProvider] throws [IllegalArgumentException] if the provider already exists;
 *  - [removeTestProvider] throws [IllegalArgumentException] if the provider does not exist.
 */
interface MockProviderBackend {
    fun addTestProvider(provider: String)
    fun setTestProviderEnabled(provider: String, enabled: Boolean)
    fun setTestProviderLocation(provider: String, fix: MockFix)
    fun removeTestProvider(provider: String)
}

/** Wall clock + monotonic clock, injectable so tests control "now". */
interface MockClock {
    fun currentTimeMillis(): Long
    fun elapsedRealtimeNanos(): Long
}

fun interface EngineLog {
    fun log(message: String)
}
