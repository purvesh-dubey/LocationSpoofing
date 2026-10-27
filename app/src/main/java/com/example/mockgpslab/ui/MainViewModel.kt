package com.example.mockgpslab.ui

import android.app.Application
import android.content.Context
import android.location.LocationManager
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.mockgpslab.MockGpsLabApplication
import com.example.mockgpslab.data.CoordinateForm
import com.example.mockgpslab.data.GeoPoint
import com.example.mockgpslab.data.SavedPlace
import com.example.mockgpslab.mock.LocationManagerBackend
import com.example.mockgpslab.mock.MockAppProbe
import com.example.mockgpslab.mock.MockAppStatus
import com.example.mockgpslab.mock.SystemLocationProbe
import com.example.mockgpslab.mock.SystemReading
import com.example.mockgpslab.service.MockLocationController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

sealed interface ReportUiState {
    data object Idle : ReportUiState
    data object Loading : ReportUiState
    data class Done(val reading: SystemReading) : ReportUiState
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as MockGpsLabApplication).container
    private val settings = container.settings
    val session = container.session

    private val mockProbe = MockAppProbe(
        backend = LocationManagerBackend(application.getSystemService(Context.LOCATION_SERVICE) as LocationManager),
        log = { Log.w(TAG, it) },
    )
    private val systemProbe = SystemLocationProbe(application)
    private val probeLock = Mutex()

    private val _form = MutableStateFlow(CoordinateForm())
    val form: StateFlow<CoordinateForm> = _form.asStateFlow()

    val savedPlaces: StateFlow<List<SavedPlace>> =
        settings.savedPlaces.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _mockStatus = MutableStateFlow(MockAppStatus.Unknown)
    val mockStatus: StateFlow<MockAppStatus> = _mockStatus.asStateFlow()

    private val _report = MutableStateFlow<ReportUiState>(ReportUiState.Idle)
    val report: StateFlow<ReportUiState> = _report.asStateFlow()

    init {
        // Restore the persisted selection into the text fields.
        viewModelScope.launch {
            val point = settings.selectedPoint.first()
            val accuracy = settings.accuracyMeters.first()
            _form.update { it.withPoint(point).withAccuracy(accuracy) }
        }
    }

    fun onLatitudeChange(text: String) = edit { it.editLatitude(text) }
    fun onLongitudeChange(text: String) = edit { it.editLongitude(text) }
    fun onAccuracyChange(text: String) = edit { it.editAccuracy(text) }
    fun selectPoint(point: GeoPoint) = edit { it.withPoint(point) }

    /**
     * Single funnel for every edit. Valid values are persisted immediately; because the service watches the same
     * DataStore values, a running mock follows the user's choice live.
     */
    private fun edit(transform: (CoordinateForm) -> CoordinateForm) {
        val before = _form.value
        val after = transform(before)
        _form.value = after
        after.point?.takeIf { it != before.point }?.let { viewModelScope.launch { settings.saveSelectedPoint(it) } }
        after.accuracyMeters?.takeIf { it != before.accuracyMeters }?.let { viewModelScope.launch { settings.saveAccuracy(it) } }
    }

    fun savePlace(name: String) {
        val point = _form.value.point ?: return
        if (name.isBlank()) return
        viewModelScope.launch { settings.addPlace(SavedPlace(name.trim(), point)) }
    }

    fun deletePlace(place: SavedPlace) {
        viewModelScope.launch { settings.deletePlace(place) }
    }

    /** Cheap enough to call on every resume, e.g. when the user returns from Developer Options. */
    fun refreshMockStatus() {
        viewModelScope.launch {
            _mockStatus.value = withContext(Dispatchers.Default) {
                probeLock.withLock {
                    // While the service runs, probing would add/remove a provider the engine is using.
                    if (session.serviceRunning.value) MockAppStatus.Selected else mockProbe.check()
                }
            }
        }
    }

    fun start(context: Context): Boolean = MockLocationController.start(context)

    fun stop(context: Context) = MockLocationController.stop(context)

    fun checkWhatSystemReports() {
        if (_report.value == ReportUiState.Loading) return
        viewModelScope.launch {
            _report.value = ReportUiState.Loading
            _report.value = ReportUiState.Done(systemProbe.read())
        }
    }

    private companion object {
        const val TAG = "MainViewModel"
    }
}
