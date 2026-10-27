package com.example.mockgpslab.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mockgpslab.data.Parsed
import com.example.mockgpslab.data.Presets
import com.example.mockgpslab.data.SavedPlace
import com.example.mockgpslab.data.errorOrNull
import com.example.mockgpslab.mock.EngineState
import com.example.mockgpslab.mock.MockAppStatus
import com.example.mockgpslab.mock.SystemReading
import com.example.mockgpslab.permissions.PermissionsCard
import com.example.mockgpslab.permissions.rememberPermissionController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onOpenSetup: () -> Unit,
    onOpenHookingLab: () -> Unit,
    viewModel: MainViewModel = viewModel(),
) {
    val context = LocalContext.current
    val form by viewModel.form.collectAsStateWithLifecycle()
    val engineState by viewModel.session.engineState.collectAsStateWithLifecycle()
    val serviceRunning by viewModel.session.serviceRunning.collectAsStateWithLifecycle()
    val mockStatus by viewModel.mockStatus.collectAsStateWithLifecycle()
    val places by viewModel.savedPlaces.collectAsStateWithLifecycle()
    val report by viewModel.report.collectAsStateWithLifecycle()
    val permissions = rememberPermissionController()

    // Re-probe whenever we return to the foreground, e.g. after picking the app in Developer Options.
    LifecycleResumeEffect(Unit) {
        viewModel.refreshMockStatus()
        onPauseOrDispose { }
    }

    val running = serviceRunning && engineState is EngineState.Running
    val canStart = permissions.status.anyLocation && form.point != null && form.accuracyMeters != null &&
        mockStatus != MockAppStatus.NotSelected
    var showSaveDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mock GPS Lab") },
                actions = {
                    TextButton(onClick = onOpenSetup) { Text("Setup") }
                    TextButton(onClick = onOpenHookingLab) { Text("Hooking Lab") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            StatusCard(
                permissions = permissions.status,
                mockStatus = mockStatus,
                engineState = engineState,
                serviceRunning = serviceRunning,
                onOpenDeveloperOptions = { DeveloperOptions.open(context) },
            )

            if (!permissions.status.fineLocation || !permissions.status.notifications) {
                PermissionsCard(permissions)
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Pick a point", style = MaterialTheme.typography.titleMedium)
                    Text("Tap the map, type coordinates, or choose a place below.", style = MaterialTheme.typography.bodySmall)
                    MapPicker(
                        point = form.point,
                        onPick = viewModel::selectPoint,
                        modifier = Modifier.fillMaxWidth().height(300.dp),
                    )
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Coordinates", style = MaterialTheme.typography.titleMedium)
                    CoordinateField("Latitude (-90 to 90)", form.latText, form.lat.errorOrNull, viewModel::onLatitudeChange)
                    CoordinateField("Longitude (-180 to 180)", form.lonText, form.lon.errorOrNull, viewModel::onLongitudeChange)
                    Text(
                        "Tip: paste \"25.5941, 85.1376\" into either field and both are filled in.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    CoordinateField("Accuracy in metres", form.accuracyText, (form.accuracy as? Parsed.Invalid)?.message, viewModel::onAccuracyChange)
                }
            }

            PlacesCard(
                places = places,
                onSelect = { viewModel.selectPoint(it.point) },
                onDelete = viewModel::deletePlace,
                onSaveCurrent = { showSaveDialog = true },
                canSave = form.point != null,
            )

            Button(
                onClick = { if (running) viewModel.stop(context) else viewModel.start(context) },
                enabled = running || canStart,
                modifier = Modifier.fillMaxWidth().height(72.dp),
                colors = if (running) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error) else ButtonDefaults.buttonColors(),
            ) {
                Text(if (running) "Stop mock location" else "Start mock location", style = MaterialTheme.typography.titleLarge)
            }
            if (!running && !canStart) {
                Text(startBlockedReason(permissions.status.anyLocation, form.point != null, form.accuracyMeters != null, mockStatus), style = MaterialTheme.typography.bodySmall)
            }

            OutlinedButton(onClick = { DeveloperOptions.open(context) }, modifier = Modifier.fillMaxWidth()) {
                Text("Open Developer Options")
            }

            SystemReportCard(report = report, onCheck = viewModel::checkWhatSystemReports)
        }
    }

    if (showSaveDialog) {
        SavePlaceDialog(
            onDismiss = { showSaveDialog = false },
            onSave = {
                viewModel.savePlace(it)
                showSaveDialog = false
            },
        )
    }
}

private fun startBlockedReason(location: Boolean, validPoint: Boolean, validAccuracy: Boolean, mock: MockAppStatus) = when {
    !location -> "Grant location permission to start."
    !validPoint -> "Enter a valid latitude and longitude, or tap the map."
    !validAccuracy -> "Enter a valid accuracy."
    mock == MockAppStatus.NotSelected -> "Select this app as the mock location app in Developer Options first."
    else -> ""
}

@Composable
private fun CoordinateField(label: String, value: String, error: String?, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        singleLine = true,
        // Plain text keyboard (not Decimal): a decimal pad has no comma or space, which pasting a pair needs.
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun PlacesCard(
    places: List<SavedPlace>,
    onSelect: (SavedPlace) -> Unit,
    onDelete: (SavedPlace) -> Unit,
    onSaveCurrent: () -> Unit,
    canSave: Boolean,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 16.dp)) {
            Text("Places", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 16.dp))
            Presets.builtIn.forEach { PlaceRow(it, onSelect, onDelete = null) }
            if (places.isNotEmpty()) {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Text("Your places", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 16.dp))
                places.forEach { PlaceRow(it, onSelect, onDelete) }
            }
            TextButton(onClick = onSaveCurrent, enabled = canSave, modifier = Modifier.padding(horizontal = 8.dp)) {
                Text("Save current point as…")
            }
        }
    }
}

@Composable
private fun PlaceRow(place: SavedPlace, onSelect: (SavedPlace) -> Unit, onDelete: ((SavedPlace) -> Unit)?) {
    Row(
        Modifier.fillMaxWidth().clickable { onSelect(place) }.padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(place.name, style = MaterialTheme.typography.bodyLarge)
            Text(place.point.toString(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (onDelete != null) {
            IconButton(onClick = { onDelete(place) }) { Icon(Icons.Filled.Close, contentDescription = "Delete ${place.name}") }
        }
    }
}

@Composable
private fun SavePlaceDialog(onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save place") },
        text = {
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true)
        },
        confirmButton = { TextButton(onClick = { onSave(name) }, enabled = name.isNotBlank()) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** The in-app proof: ask the system for a location like any other app would and show what comes back. */
@Composable
private fun SystemReportCard(report: ReportUiState, onCheck: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("What the system reports", style = MaterialTheme.typography.titleMedium)
            Text(
                "Asks Android's LocationManager for a location, exactly as any other app would. " +
                    "With the mock running you should see your chosen point, flagged as mock.",
                style = MaterialTheme.typography.bodySmall,
            )
            Button(onClick = onCheck, enabled = report != ReportUiState.Loading) {
                Text(if (report == ReportUiState.Loading) "Asking the system…" else "Check now")
            }
            when (report) {
                ReportUiState.Idle, ReportUiState.Loading -> Unit
                is ReportUiState.Done -> when (val reading = report.reading) {
                    is SystemReading.Fix -> {
                        ReportLine("Latitude / longitude", reading.point.toString())
                        ReportLine("Provider", reading.provider)
                        ReportLine("Flagged as mock", if (reading.isMock) "Yes" else "No")
                        reading.accuracyMeters?.let { ReportLine("Accuracy", "%.1f m".format(java.util.Locale.US, it)) }
                        ReportLine("Fix age", "${reading.ageMillis} ms")
                    }
                    SystemReading.NoPermission -> Text("Grant location permission first.", color = MaterialTheme.colorScheme.error)
                    SystemReading.NoFix -> Text(
                        "No location came back. Turn Location on in quick settings, or start the mock first.",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun ReportLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
