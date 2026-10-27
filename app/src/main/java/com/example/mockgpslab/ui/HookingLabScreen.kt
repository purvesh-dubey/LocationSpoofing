package com.example.mockgpslab.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mockgpslab.permissions.rememberPermissionController

/**
 * Phase 2 in one screen: the same call made two ways. "Real" talks straight to Android's LocationManager;
 * "Hooked" goes through a demo client app whose service lookup a java.lang.reflect.Proxy may have replaced.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HookingLabScreen(onBack: () -> Unit, viewModel: HookingLabViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val entries by viewModel.log.entries.collectAsStateWithLifecycle()
    val permissions = rememberPermissionController()

    LaunchedEffect(Unit) { viewModel.query() }
    // Re-read after the user grants location, so the Real column updates without another tap.
    LaunchedEffect(permissions.status.fineLocation) { viewModel.query() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Hooking Lab") },
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
                "A demo client app asks a service registry for \"the location service\". Turning the hook on swaps " +
                    "that entry for a java.lang.reflect.Proxy that answers with the point selected on the main screen. " +
                    "Only our own demo classes are touched; no other app and no system service is affected.",
                style = MaterialTheme.typography.bodyMedium,
            )

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ToggleRow("Hook enabled", "Swap the registry entry for a logging proxy.", state.hookOn, viewModel::setHook)
                    ToggleRow(
                        "Patch the client's cached reference",
                        "Reflection: replace the private field the client cached earlier (step 2.4's fix).",
                        state.patchCachedField,
                        viewModel::setPatchCachedField,
                    )
                    Text(
                        "Client has cached the service: ${if (state.clientHasCachedService) "yes" else "no"}   •   " +
                            "fields patched: ${state.patchedFields}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = viewModel::query) { Text("Query again") }
                        OutlinedButton(onClick = viewModel::newClient) { Text("New client app") }
                    }
                }
            }

            if (state.clientIsStale) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Stale cache", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "The hook is installed, but this client looked the service up BEFORE that and kept the " +
                                "original in a private field. It never asks the registry again, so it can't see the " +
                                "proxy. Turn on \"Patch the client's cached reference\", or tap \"New client app\".",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ResultCard(
                    "Real",
                    "LocationManager.getLastKnownLocation(\"gps\")",
                    state.real,
                    state.realGpsEnabled,
                    Modifier.weight(1f),
                )
                ResultCard(
                    "Hooked",
                    "Same call through DemoClientApp",
                    state.hooked,
                    state.hookedGpsEnabled,
                    Modifier.weight(1f),
                )
            }

            if (!permissions.status.fineLocation) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "Reading the real last-known location needs location permission. The hooked column can still " +
                                "answer without it, which is exactly why hooking is powerful (and why Android guards the real call).",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Button(onClick = if (permissions.locationPermanentlyDenied) permissions.openAppSettings else permissions.requestLocation) {
                            Text(if (permissions.locationPermanentlyDenied) "Open app settings" else "Grant location")
                        }
                    }
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Intercepted calls", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        TextButton(onClick = viewModel::clearLog, enabled = entries.isNotEmpty()) { Text("Clear") }
                    }
                    if (entries.isEmpty()) {
                        Text(
                            "Nothing yet. Only the proxy logs, so this stays empty while the hook is off. Turn it on and tap Query again.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    } else {
                        // Bounded height so this list can live inside the screen's own vertical scroll.
                        LazyColumn(Modifier.fillMaxWidth().height(260.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(entries.asReversed(), key = { it.sequence }) { entry ->
                                Text(entry.format(), fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 15.sp)
                            }
                        }
                    }
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Try this", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "1. Hook off, Query: the client looks the service up and caches it.\n" +
                            "2. Hook on with patching off, Query: Hooked still shows the real value (stale cache).\n" +
                            "3. Switch patching on: the Hooked column changes to your selected point.\n" +
                            "4. Pick another place on the main screen and Query again: the hook follows it.\n" +
                            "Tip: start the mock on the main screen first, or the real column may have no fix to show.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun ResultCard(title: String, subtitle: String, result: LabResult?, gpsEnabled: Boolean?, modifier: Modifier = Modifier) {
    Card(modifier) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.labelSmall)
            Text(
                when (result) {
                    null -> "…"
                    is LabResult.Point -> result.point.toString()
                    LabResult.NoFix -> "null (no last-known fix)"
                    LabResult.NoPermission -> "No location permission"
                    is LabResult.Failed -> "Error: ${result.message}"
                },
                style = MaterialTheme.typography.bodyLarge,
                color = if (result is LabResult.Failed || result == LabResult.NoPermission) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                "gps enabled: ${gpsEnabled ?: "?"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
