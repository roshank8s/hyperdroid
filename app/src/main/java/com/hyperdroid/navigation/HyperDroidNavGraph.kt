package com.hyperdroid.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.hyperdroid.ui.createvm.CreateVMScreen
import com.hyperdroid.ui.home.HomeScreen
import com.hyperdroid.ui.settings.SettingsScreen
import com.hyperdroid.ui.setup.SetupWizardScreen
import com.hyperdroid.ui.terminal.TerminalScreen
import kotlinx.serialization.Serializable

@Serializable
object SetupWizardRoute

@Serializable
object HomeRoute

@Serializable
object SettingsRoute

@Serializable
object CreateVMRoute

@Serializable
data class TerminalRoute(val vmId: String)

@Composable
fun HyperDroidNavGraph(
    startDestination: Any = SetupWizardRoute,
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable<SetupWizardRoute> {
            SetupWizardScreen(
                onSetupComplete = {
                    navController.navigate(HomeRoute) {
                        popUpTo(SetupWizardRoute) { inclusive = true }
                    }
                }
            )
        }
        composable<HomeRoute> {
            HomeScreen(
                onNavigateToSettings = { navController.navigate(SettingsRoute) },
                onNavigateToCreateVM = { navController.navigate(CreateVMRoute) },
                onNavigateToTerminal = { vmId -> navController.navigate(TerminalRoute(vmId)) }
            )
        }
        composable<CreateVMRoute> {
            CreateVMScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable<TerminalRoute> {
            TerminalScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable<SettingsRoute> {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() },
                onRerunSetup = {
                    navController.navigate(SetupWizardRoute) {
                        popUpTo(HomeRoute) { inclusive = true }
                    }
                }
            )
        }
    }
}
