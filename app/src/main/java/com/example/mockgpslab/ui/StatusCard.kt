package com.example.mockgpslab.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.mockgpslab.mock.EngineState
import com.example.mockgpslab.mock.MockAppStatus
import com.example.mockgpslab.permissions.PermissionStatus

private enum class Tone { Good, Bad, Neutral }

@Composable
private fun toneColor(tone: Tone): Color = when (tone) {
    Tone.Good -> MaterialTheme.colorScheme.primary
    Tone.Bad -> MaterialTheme.colorScheme.error
    Tone.Neutral -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
private fun StatusRow(label: String, value: String, tone: Tone) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = toneColor(tone))
    }
}

/**
 * The one-glance answer to "why isn't it working?": permissions, mock-app selection, service state.
 * [mockStatus] is the probe result; a live engine state overrides it because it is better evidence.
 */
@Composable
fun StatusCard(
    permissions: PermissionStatus,
    mockStatus: MockAppStatus,
    engineState: EngineState,
    serviceRunning: Boolean,
    onOpenDeveloperOptions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val effectiveMock = when (engineState) {
        is EngineState.Running -> MockAppStatus.Selected
        EngineState.NotMockApp -> MockAppStatus.NotSelected
        else -> mockStatus
    }
    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Status", style = MaterialTheme.typography.titleMedium)

            StatusRow(
                "Location permission",
                when {
                    permissions.fineLocation -> "Granted"
                    permissions.approximateOnly -> "Approximate only"
                    else -> "Not granted"
                },
                if (permissions.fineLocation) Tone.Good else Tone.Bad,
            )
            if (com.example.mockgpslab.permissions.Permissions.needsNotificationPermission) {
                StatusRow(
                    "Notifications",
                    if (permissions.notifications) "Allowed" else "Blocked",
                    if (permissions.notifications) Tone.Good else Tone.Bad,
                )
            }
            StatusRow(
                "Selected as mock app",
                when (effectiveMock) {
                    MockAppStatus.Selected -> "Yes"
                    MockAppStatus.NotSelected -> "No"
                    MockAppStatus.Unknown -> "Unknown"
                },
                when (effectiveMock) {
                    MockAppStatus.Selected -> Tone.Good
                    MockAppStatus.NotSelected -> Tone.Bad
                    MockAppStatus.Unknown -> Tone.Neutral
                },
            )
            StatusRow(
                "Mock service",
                if (serviceRunning && engineState is EngineState.Running) "Running" else "Stopped",
                if (serviceRunning && engineState is EngineState.Running) Tone.Good else Tone.Neutral,
            )

            if (engineState is EngineState.Running) {
                Text(
                    "Reporting ${"%.6f".format(java.util.Locale.US, engineState.latitude)}, " +
                        "${"%.6f".format(java.util.Locale.US, engineState.longitude)} to the system.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (effectiveMock == MockAppStatus.NotSelected) {
                Text(
                    "Android only lets one app mock location, and you choose it in Developer Options under " +
                        "\"Select mock location app\".",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                TextButton(onClick = onOpenDeveloperOptions) { Text("Open Developer Options") }
            }
            if (engineState is EngineState.Error) {
                Text("Error: ${engineState.message}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
