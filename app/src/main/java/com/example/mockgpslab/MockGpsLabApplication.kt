package com.example.mockgpslab

import android.app.Application
import org.osmdroid.config.Configuration
import java.io.File

class MockGpsLabApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        configureOsmdroid()
    }

    private fun configureOsmdroid() {
        Configuration.getInstance().apply {
            // OpenStreetMap's tile usage policy requires an identifying User-Agent; the default
            // (a generic library string) gets blocked. Name the app and say what it is.
            userAgentValue = "MockGpsLab/${BuildConfig.VERSION_NAME} ($packageName; personal learning project)"
            // Keep tiles in the app cache so we don't need any storage permission.
            osmdroidBasePath = File(cacheDir, "osmdroid")
            osmdroidTileCache = File(cacheDir, "osmdroid/tiles")
        }
    }
}
