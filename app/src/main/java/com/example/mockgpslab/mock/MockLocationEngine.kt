package com.example.mockgpslab.mock

import com.example.mockgpslab.data.GeoPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Registers "test providers" with the system and keeps feeding them one fake fix per second.
 *
 * This is the official mock-location mechanism: once the user selects this app under Developer Options,
 * LocationManager lets us replace what the gps / network / fused providers report. Every app that asks the
 * system for location then sees our fixes (flagged as mock).
 *
 * Threading: all state is guarded by [lock]; start/stop/updateTarget may be called from any thread and the
 * push loop runs on [scope]. The lock is what guarantees we never push to a provider that stop() has
 * already removed.
 */
class MockLocationEngine(
    private val backend: MockProviderBackend,
    private val scope: CoroutineScope,
    private val providers: List<MockProviderSpec>,
    private val clock: MockClock,
    private val log: EngineLog = EngineLog {},
    private val altitudeMeters: Double = 0.0,
    private val pushIntervalMillis: Long = 1_000L,
    /** Called once per tick, after the providers were fed. The optional Fused mock hangs off this. */
    private val onFix: (MockFix) -> Unit = {},
) {
    private val lock = Any()
    private val _state = MutableStateFlow<EngineState>(EngineState.Idle)
    val state: StateFlow<EngineState> = _state

    private var loop: Job? = null

    /** Providers we successfully added, in order. Cleanup walks this list, never `providers`. */
    private val added = mutableListOf<String>()
    private var target = GeoPoint.PATNA
    private var accuracyMeters = DEFAULT_ACCURACY_METERS

    fun start(point: GeoPoint, accuracyMeters: Float = DEFAULT_ACCURACY_METERS) = synchronized(lock) {
        // Starting twice must not stack two loops or leave providers half-registered.
        teardownLocked()

        target = point
        this.accuracyMeters = accuracyMeters

        try {
            for ((index, spec) in providers.withIndex()) addProviderLocked(spec, isFirst = index == 0)
        } catch (e: SecurityException) {
            log.log("Not the selected mock location app: $e")
            teardownLocked()
            _state.value = EngineState.NotMockApp
            return@synchronized
        } catch (e: Exception) {
            log.log("Failed to start mock providers: $e")
            teardownLocked()
            _state.value = EngineState.Error(e.message ?: e::class.java.simpleName)
            return@synchronized
        }

        _state.value = EngineState.Running(point.latitude, point.longitude)
        loop = scope.launch {
            while (isActive) {
                if (!pushOnce()) break
                delay(pushIntervalMillis)
            }
        }
    }

    /** Takes effect on the next tick. A no-op unless running. */
    fun updateTarget(point: GeoPoint) = synchronized(lock) {
        target = point
        if (_state.value is EngineState.Running) {
            _state.value = EngineState.Running(point.latitude, point.longitude)
        }
    }

    fun setAccuracy(meters: Float) = synchronized(lock) { accuracyMeters = meters }

    /** Safe to call at any time, any number of times. */
    fun stop() = synchronized(lock) {
        teardownLocked()
        _state.value = EngineState.Idle
    }

    private fun addProviderLocked(spec: MockProviderSpec, isFirst: Boolean) {
        val name = spec.name
        try {
            // A provider left behind by a killed process would make addTestProvider throw
            // IllegalArgumentException("already exists"), so always clear first. Throwing here
            // just means there was nothing to clear.
            try {
                backend.removeTestProvider(name)
            } catch (_: IllegalArgumentException) {
            }
            backend.addTestProvider(name)
            // Record it BEFORE enabling: if enabling fails we still want teardown to remove it.
            added += name
            backend.setTestProviderEnabled(name, true)
        } catch (e: Exception) {
            // Platforms may reject extras (e.g. "fused" on some builds), so optional providers are skipped.
            // But a SecurityException on the very first provider is how we learn we're not the mock app,
            // and that must never be swallowed.
            val skippable = spec.optional && !(isFirst && e is SecurityException)
            if (!skippable) throw e
            log.log("Optional provider '$name' rejected, continuing without it: $e")
            if (added.remove(name)) removeFromBackendQuietly(name)
        }
    }

    /** One tick. Returns false once the loop must stop because something failed (state already updated). */
    private fun pushOnce(): Boolean = synchronized(lock) {
        // stop() may have run while we waited for the lock; never touch removed providers.
        if (_state.value !is EngineState.Running) return@synchronized false

        val fix = MockFix(
            latitude = target.latitude,
            longitude = target.longitude,
            accuracyMeters = accuracyMeters,
            altitudeMeters = altitudeMeters,
            speedMetersPerSecond = 0f,
            bearingDegrees = 0f,
            timeMillis = clock.currentTimeMillis(),
            elapsedRealtimeNanos = clock.elapsedRealtimeNanos(),
        )
        try {
            for (name in added) backend.setTestProviderLocation(name, fix)
        } catch (e: SecurityException) {
            // The user un-selected the mock app (or revoked the op) while we were running.
            log.log("Lost mock-location permission while running: $e")
            teardownLocked()
            _state.value = EngineState.NotMockApp
            return@synchronized false
        } catch (e: Exception) {
            log.log("Push failed: $e")
            teardownLocked()
            _state.value = EngineState.Error(e.message ?: e::class.java.simpleName)
            return@synchronized false
        }
        runCatching { onFix(fix) }.onFailure { log.log("onFix listener failed: $it") }
        true
    }

    /**
     * Stops the loop and unregisters everything we added. This is the "always cleans up" guarantee:
     * each call is isolated so one failure cannot leave the other providers registered with the system.
     */
    private fun teardownLocked() {
        loop?.cancel()
        loop = null
        // Newest first, like unwinding a stack. Iterate a copy because we clear `added` at the end.
        for (name in added.toList().asReversed()) {
            try {
                backend.setTestProviderEnabled(name, false)
            } catch (e: Exception) {
                log.log("Disabling '$name' failed: $e")
            }
            removeFromBackendQuietly(name)
        }
        added.clear()
    }

    private fun removeFromBackendQuietly(name: String) {
        try {
            backend.removeTestProvider(name)
        } catch (e: Exception) {
            log.log("Removing '$name' failed: $e")
        }
    }

    companion object {
        const val DEFAULT_ACCURACY_METERS = 5f
    }
}
