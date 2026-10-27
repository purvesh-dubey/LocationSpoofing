package com.example.mockgpslab.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.mockgpslab.mock.MockLocationEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "mock_gps_lab")

/**
 * Everything that must survive process death. This is also THE shared "selected location":
 * the Phase 1 service reads it to know what to mock, and the Phase 2 hook reads it to know what to fake.
 */
class AppSettings(private val store: DataStore<Preferences>) {
    constructor(context: Context) : this(context.applicationContext.dataStore)

    private val data: Flow<Preferences> = store.data.catch { e ->
        // A corrupt or unreadable file shouldn't brick the app; fall back to defaults.
        if (e is IOException) emit(emptyPreferences()) else throw e
    }

    val selectedPoint: Flow<GeoPoint> = data
        .map { GeoPoint(it[LAT] ?: GeoPoint.PATNA.latitude, it[LON] ?: GeoPoint.PATNA.longitude) }
        .distinctUntilChanged()

    suspend fun saveSelectedPoint(point: GeoPoint) {
        store.edit {
            it[LAT] = point.latitude
            it[LON] = point.longitude
        }
    }

    val accuracyMeters: Flow<Float> = data
        .map { it[ACCURACY] ?: MockLocationEngine.DEFAULT_ACCURACY_METERS }
        .distinctUntilChanged()

    suspend fun saveAccuracy(meters: Float) {
        store.edit { it[ACCURACY] = meters }
    }

    private companion object {
        val LAT = doublePreferencesKey("selected_lat")
        val LON = doublePreferencesKey("selected_lon")
        val ACCURACY = floatPreferencesKey("accuracy_m")
    }
}
