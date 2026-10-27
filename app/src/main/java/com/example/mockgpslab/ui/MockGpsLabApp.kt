package com.example.mockgpslab.ui

import androidx.compose.runtime.Composable
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
        composable(Routes.HOOKING_LAB) { HookingLabScreen(onBack = { nav.popBackStack() }) }
    }
}
