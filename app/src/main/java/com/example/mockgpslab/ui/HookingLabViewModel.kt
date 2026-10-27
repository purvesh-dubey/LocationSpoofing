package com.example.mockgpslab.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.mockgpslab.MockGpsLabApplication
import com.example.mockgpslab.data.GeoPoint
import com.example.mockgpslab.hooking.CallLog
import com.example.mockgpslab.hooking.DemoClientApp
import com.example.mockgpslab.hooking.HookInstallation
import com.example.mockgpslab.hooking.HookInstaller
import com.example.mockgpslab.hooking.ServiceRegistry
import com.example.mockgpslab.hooking.android.RealLocationService
import com.example.mockgpslab.permissions.Permissions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What one column of the Lab shows for a getLastKnownLocation call. */
sealed interface LabResult {
    data class Point(val point: GeoPoint) : LabResult

    /** The call worked but there is no last-known location (provider never had a fix). */
    data object NoFix : LabResult

    data object NoPermission : LabResult
    data class Failed(val message: String) : LabResult
}

data class LabUiState(
    val hookOn: Boolean = false,
    val patchCachedField: Boolean = true,
    val clientHasCachedService: Boolean = false,
    val patchedFields: Int = 0,
    val real: LabResult? = null,
    val hooked: LabResult? = null,
    val realGpsEnabled: Boolean? = null,
    val hookedGpsEnabled: Boolean? = null,
) {
    /** The hook is installed but the client is still talking to the original: the lesson of step 2.4. */
    val clientIsStale: Boolean get() = hookOn && clientHasCachedService && patchedFields == 0
}

/**
 * Owns a private ServiceRegistry, a DemoClientApp and the hook. Everything here is in-process and only affects
 * the demo client; it never touches the real system or any other app.
 */
class HookingLabViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as MockGpsLabApplication).container

    val log = CallLog()

    private val realService = RealLocationService(application)
    private val registry = ServiceRegistry().apply { put(ServiceRegistry.LOCATION, realService) }
    private var client = DemoClientApp(registry)
    private var installation: HookInstallation? = null

    private val _state = MutableStateFlow(LabUiState())
    val state: StateFlow<LabUiState> = _state.asStateFlow()

    fun setHook(on: Boolean) {
        if (on && installation == null) {
            val installed = HookInstaller.installLocationHook(
                registry = registry,
                // The same selected point Phase 1 mocks: one choice, both phases.
                fakePoint = { container.selectedPoint.value },
                log = log,
            )
            val patched = if (_state.value.patchCachedField) installed.patchCachedReferences(client) else 0
            installation = installed
            _state.update { it.copy(hookOn = true, patchedFields = patched) }
        } else if (!on) {
            installation?.uninstall()
            installation = null
            _state.update { it.copy(hookOn = false, patchedFields = 0) }
        }
        query()
    }

    /** Switching this while the hook is on applies immediately, so the stale-cache fix can be seen live. */
    fun setPatchCachedField(on: Boolean) {
        val installed = installation
        val patched = when {
            installed == null -> 0
            on -> installed.patchCachedReferences(client)
            else -> {
                installed.unpatchCachedReferences()
                0
            }
        }
        _state.update { it.copy(patchCachedField = on, patchedFields = patched) }
        query()
    }

    /** A brand-new client has an empty cache, like an app process that starts after the hook was installed. */
    fun newClient() {
        installation?.unpatchCachedReferences()
        client = DemoClientApp(registry)
        _state.update { it.copy(patchedFields = 0) }
        query()
    }

    fun clearLog() = log.clear()

    fun query() {
        viewModelScope.launch(Dispatchers.Default) {
            val fine = Permissions.read(getApplication()).fineLocation
            val real = evaluate(fine) { realService.getLastKnownLocation(PROVIDER) }
            val hooked = evaluate(fine) { client.whereAmI(PROVIDER) }
            val realEnabled = runCatching { realService.isProviderEnabled(PROVIDER) }.getOrNull()
            val hookedEnabled = runCatching { client.isGpsEnabled() }.getOrNull()
            _state.update {
                it.copy(
                    real = real,
                    hooked = hooked,
                    realGpsEnabled = realEnabled,
                    hookedGpsEnabled = hookedEnabled,
                    clientHasCachedService = client.hasCachedService,
                )
            }
        }
    }

    private fun evaluate(hasFinePermission: Boolean, call: () -> GeoPoint?): LabResult = try {
        when (val point = call()) {
            null -> if (hasFinePermission) LabResult.NoFix else LabResult.NoPermission
            else -> LabResult.Point(point)
        }
    } catch (e: Exception) {
        LabResult.Failed(e.message ?: e::class.java.simpleName)
    }

    private companion object {
        const val PROVIDER = "gps"
    }
}
