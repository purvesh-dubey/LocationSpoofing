package com.example.mockgpslab.data

/**
 * A latitude/longitude pair. Our own tiny type (no android.location / osmdroid import) so that code shared
 * by Phase 1 and the Phase 2 hooking core stays testable on the plain JVM.
 */
data class GeoPoint(val latitude: Double, val longitude: Double) {
    override fun toString() = "%.6f, %.6f".format(java.util.Locale.US, latitude, longitude)

    companion object {
        /** Patna, the default starting point. */
        val PATNA = GeoPoint(25.5941, 85.1376)
    }
}
