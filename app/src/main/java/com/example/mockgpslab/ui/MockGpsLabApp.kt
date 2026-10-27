package com.example.mockgpslab.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mockgpslab.MockGpsLabApplication
import com.example.mockgpslab.service.MockLocationController
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.mockgpslab.permissions.PermissionsCard
import com.example.mockgpslab.permissions.rememberPermissionController

/** Route names live in one place so screens never hard-code strings. */
object Routes {
    const val MAIN = "main"
    const val SETUP = "setup"
    const val HOOKING_LAB = "hooking_lab"
}

@Composable
fun MockGpsLabApp() {
    val nav = rememberNavController()
    Scaffold { padding ->
        NavHost(nav, startDestination = Routes.MAIN, modifier = Modifier.padding(padding)) {
            composable(Routes.MAIN) {
                // Temporary: the real Main screen arrives in step 1.5; this proves the permission flow end to end.
                TemporaryServiceControls()
            }
            composable(Routes.SETUP) { Placeholder("Setup checklist") }
            composable(Routes.HOOKING_LAB) { Placeholder("Hooking Lab (Phase 2)") }
        }
    }
}

@Composable
private fun Placeholder(title: String) {
    Box(Modifier, contentAlignment = Alignment.Center) { Text(title) }
}

@Composable
private fun TemporaryServiceControls() {
    val context = LocalContext.current
    val session = (context.applicationContext as MockGpsLabApplication).container.session
    val engineState by session.engineState.collectAsStateWithLifecycle()
    val permissions = rememberPermissionController()
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PermissionsCard(permissions)
        Text("Engine: $engineState")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { MockLocationController.start(context) }, enabled = permissions.status.anyLocation) { Text("Start") }
            Button(onClick = { MockLocationController.stop(context) }) { Text("Stop") }
        }
    }
}
