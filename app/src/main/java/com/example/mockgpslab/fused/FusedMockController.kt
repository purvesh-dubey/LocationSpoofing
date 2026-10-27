package com.example.mockgpslab.fused

import android.annotation.SuppressLint
import android.content.Context
import com.example.mockgpslab.mock.EngineLog
import com.example.mockgpslab.mock.MockFix
import com.example.mockgpslab.mock.toLocation
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices

sealed interface FusedState {
    data object Off : FusedState
    data object Active : FusedState

    /** Google Play services is missing or out of date (common on emulators without Google APIs, some Chinese ROMs). */
    data object Unavailable : FusedState
    data class Failed(val message: String) : FusedState
}

/**
 * Mocks the Google Play services "Fused Location Provider" (FusedLocationProviderClient).
 *
 * Why this exists next to the LocationManager mock: most real apps (Google Maps included) don't talk to
 * LocationManager directly. They use FusedLocationProviderClient, which lives in Play services and blends GPS,
 * Wi-Fi and cell data. LocationManager test providers feed the platform fused provider, but Play services'
 * own fused implementation has a separate switch: setMockMode(true) makes it report whatever we pass to
 * setMockLocation(). It still requires this app to be the selected mock location app.
 *
 * Everything degrades gracefully: if Play services is missing, or a call is refused, we report a state and the
 * LocationManager mock carries on unaffected.
 */
// ACCESS_MOCK_LOCATION is a development permission: it cannot be requested or checked at runtime, it is "granted"
// by choosing this app under Developer Options. Lint can't model that, so the missing-check warning is a false alarm.
@SuppressLint("MissingPermission")
class FusedMockController(
    private val context: Context,
    private val onState: (FusedState) -> Unit,
    private val log: EngineLog = EngineLog {},
) {
    private var client: FusedLocationProviderClient? = null
    private var active = false
    private var failureLogged = false

    fun enable() {
        if (!isPlayServicesAvailable(context)) {
            log.log("Fused mock skipped: Google Play services is not available")
            onState(FusedState.Unavailable)
            return
        }
        try {
            val fused = LocationServices.getFusedLocationProviderClient(context)
            client = fused
            fused.setMockMode(true)
                .addOnSuccessListener {
                    active = true
                    onState(FusedState.Active)
                }
                .addOnFailureListener { fail("setMockMode(true) failed", it) }
        } catch (e: Exception) {
            fail("Could not start the fused mock", e)
        }
    }

    /** Called once per engine tick. Dropped until setMockMode(true) has been acknowledged. */
    fun push(fix: MockFix) {
        val fused = client?.takeIf { active } ?: return
        try {
            fused.setMockLocation(fix.toLocation(FUSED_PROVIDER))
                .addOnFailureListener { fail("setMockLocation failed", it) }
        } catch (e: Exception) {
            fail("setMockLocation threw", e)
        }
    }

    /** Always call on stop: mock mode would otherwise stay on until this process dies. */
    fun disable() {
        val fused = client
        client = null
        active = false
        if (fused != null) {
            try {
                fused.setMockMode(false).addOnFailureListener { log.log("setMockMode(false) failed: $it") }
            } catch (e: Exception) {
                log.log("setMockMode(false) threw: $e")
            }
        }
        onState(FusedState.Off)
    }

    private fun fail(what: String, cause: Throwable) {
        active = false
        if (!failureLogged) {
            failureLogged = true
            log.log("$what: $cause")
        }
        onState(FusedState.Failed(cause.message ?: cause::class.java.simpleName))
    }

    companion object {
        // Same string as LocationManager.FUSED_PROVIDER, which is API 31+; the Location's provider name is only a label here.
        private const val FUSED_PROVIDER = "fused"

        fun isPlayServicesAvailable(context: Context): Boolean = try {
            GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS
        } catch (_: Throwable) {
            false
        }
    }
}
