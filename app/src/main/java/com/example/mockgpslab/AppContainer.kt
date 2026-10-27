package com.example.mockgpslab

import android.app.Application
import com.example.mockgpslab.data.AppSettings
import com.example.mockgpslab.data.GeoPoint
import com.example.mockgpslab.service.MockSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/** Hand-rolled dependency holder: three objects don't justify a DI framework. */
class AppContainer(application: Application) {
    /** Lives as long as the process; for work that must not die with a screen. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val settings = AppSettings(application)
    val session = MockSession()

    /**
     * Latest selected point as a plain value. Phase 2's hook is synchronous (it runs inside a method call),
     * so it cannot suspend to read DataStore; it reads this instead.
     */
    val selectedPoint: StateFlow<GeoPoint> =
        settings.selectedPoint.stateIn(appScope, SharingStarted.Eagerly, GeoPoint.PATNA)
}
