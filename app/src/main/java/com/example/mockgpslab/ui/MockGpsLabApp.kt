package com.example.mockgpslab.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

/** Route names live in one place so screens never hard-code strings. */
object Routes {
    const val MAIN = "main"
    const val SETUP = "setup"
    const val HOOKING_LAB = "hooking_lab"
}

@Composable
fun MockGpsLabApp() {
    val nav = rememberNavController()
    // Each screen owns its Scaffold (top bar + insets), so the host stays a bare NavHost.
    NavHost(nav, startDestination = Routes.MAIN) {
        composable(Routes.MAIN) {
            MainScreen(
                onOpenSetup = { nav.navigate(Routes.SETUP) },
                onOpenHookingLab = { nav.navigate(Routes.HOOKING_LAB) },
            )
        }
        composable(Routes.SETUP) { SetupScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.HOOKING_LAB) { HookingLabPlaceholder(onBack = { nav.popBackStack() }) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HookingLabPlaceholder(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Hooking Lab") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding), contentAlignment = Alignment.Center) { Text("Coming in Phase 2") }
    }
}
