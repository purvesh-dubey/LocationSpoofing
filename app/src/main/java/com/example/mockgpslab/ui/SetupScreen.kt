package com.example.mockgpslab.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mockgpslab.mock.EngineState
import com.example.mockgpslab.mock.MockAppStatus
import com.example.mockgpslab.permissions.Permissions
import com.example.mockgpslab.permissions.rememberPermissionController

/** A step-by-step checklist with live ticks, so the user can see which prerequisite is still missing. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(onBack: () -> Unit, viewModel: MainViewModel = viewModel()) {
    val context = LocalContext.current
    val permissions = rememberPermissionController()
    val mockStatus by viewModel.mockStatus.collectAsStateWithLifecycle()
    val engineState by viewModel.session.engineState.collectAsStateWithLifecycle()
    var developerOptionsOn by remember { mutableStateOf(DeveloperOptions.isEnabled(context)) }

    LifecycleResumeEffect(Unit) {
        developerOptionsOn = DeveloperOptions.isEnabled(context)
        viewModel.refreshMockStatus()
        onPauseOrDispose { }
    }

    val mockSelected = mockStatus == MockAppStatus.Selected || engineState is EngineState.Running

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Setup checklist") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "Android only lets an app feed fake locations to the system after you opt in. " +
                    "Work through these four steps; each tick updates by itself when you come back.",
                style = MaterialTheme.typography.bodyMedium,
            )

            StepCard(
                number = 1,
                title = "Enable Developer Options",
                done = developerOptionsOn,
                body = "Open Settings > About phone and tap \"Build number\" seven times. You'll be asked for your " +
                    "screen lock. Developer Options then appears under Settings > System (or at the top level, " +
                    "depending on the phone).",
            )

            StepCard(
                number = 2,
                title = "Select this app as the mock location app",
                done = mockSelected,
                body = "In Developer Options, scroll to \"Select mock location app\" and choose Mock GPS Lab. " +
                    "This is the opt-in that makes Android accept our test providers; there is no way around it, " +
                    "and only one app can hold it at a time.",
                actionLabel = "Open Developer Options",
                onAction = { DeveloperOptions.open(context) },
            )

            StepCard(
                number = 3,
                title = "Grant location permission",
                done = permissions.status.fineLocation,
                body = "Needed by the foreground service and by the \"what the system reports\" check. " +
                    "Choose \"Precise\" if Android asks. Your real location never leaves the phone.",
                actionLabel = if (permissions.locationPermanentlyDenied) "Open app settings" else "Grant location",
                onAction = if (permissions.locationPermanentlyDenied) permissions.openAppSettings else permissions.requestLocation,
            )

            if (Permissions.needsNotificationPermission) {
                StepCard(
                    number = 4,
                    title = "Allow notifications",
                    done = permissions.status.notifications,
                    body = "The mock runs as a foreground service whose notification shows what is being reported and " +
                        "has a Stop button. Without this permission the mock still works but you can't see or stop it " +
                        "from the notification shade.",
                    actionLabel = if (permissions.notificationsPermanentlyDenied) "Open app settings" else "Allow notifications",
                    onAction = if (permissions.notificationsPermanentlyDenied) permissions.openAppSettings else permissions.requestNotifications,
                )
            }

            Text(
                "Then go back, pick a point, press Start, and use \"What the system reports\" to confirm.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun StepCard(
    number: Int,
    title: String,
    done: Boolean,
    body: String,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (done) "✓" else "○",
                    color = if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text("$number. $title", style = MaterialTheme.typography.titleMedium)
            }
            Text(body, style = MaterialTheme.typography.bodyMedium)
            if (actionLabel != null && !done) Button(onClick = onAction) { Text(actionLabel) }
        }
    }
}
