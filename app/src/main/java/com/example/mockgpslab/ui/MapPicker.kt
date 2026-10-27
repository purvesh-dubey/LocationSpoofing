package com.example.mockgpslab.ui

import android.annotation.SuppressLint
import android.view.MotionEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.mockgpslab.data.GeoPoint
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint as OsmGeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker

/** Remembers whether the last move came from a map tap, so we don't re-centre under the user's finger. */
private class TapTracker {
    var lastTap: GeoPoint? = null
    var centred = false
}

/**
 * OpenStreetMap view (osmdroid): tap to choose a point, a marker shows the current selection.
 * osmdroid is a classic View, so it is hosted via AndroidView and its lifecycle is forwarded by hand.
 */
@SuppressLint("ClickableViewAccessibility")
@Composable
fun MapPicker(point: GeoPoint?, onPick: (GeoPoint) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnPick by rememberUpdatedState(onPick)
    val tracker = remember { TapTracker() }

    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.SHOW_AND_FADEOUT)
            controller.setZoom(14.0)
            // The map sits inside a scrolling screen. Without this the parent steals vertical drags
            // and the map can only be panned sideways.
            setOnTouchListener { view, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> view.parent?.requestDisallowInterceptTouchEvent(true)
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> view.parent?.requestDisallowInterceptTouchEvent(false)
                }
                false
            }
        }
    }
    val marker = remember(mapView) {
        Marker(mapView).apply {
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            // No title means no info bubble; swallow marker taps so they act like map taps instead.
            setOnMarkerClickListener { _, _ -> true }
        }
    }

    DisposableEffect(mapView, lifecycleOwner) {
        val receiver = object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: OsmGeoPoint): Boolean {
                val picked = GeoPoint(p.latitude, p.longitude)
                tracker.lastTap = picked
                currentOnPick(picked)
                return true
            }

            override fun longPressHelper(p: OsmGeoPoint) = false
        }
        mapView.overlays.add(MapEventsOverlay(receiver))
        mapView.overlays.add(marker)

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.overlays.clear()
            mapView.onDetach()
        }
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier,
        update = { view ->
            if (point != null) {
                val position = OsmGeoPoint(point.latitude, point.longitude)
                marker.position = position
                when {
                    !tracker.centred -> {
                        view.controller.setCenter(position)
                        tracker.centred = true
                    }
                    // Typed or preset coordinates move the camera; map taps don't (the user is already looking).
                    point != tracker.lastTap -> view.controller.animateTo(position)
                }
                view.invalidate()
            }
        },
    )
}
