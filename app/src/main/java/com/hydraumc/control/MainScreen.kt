package com.hydraumc.control

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ControlCamera
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.hydraumc.control.ui.*
import com.hydraumc.control.viewmodel.RobotViewModel

sealed class Screen(val route: String, @StringRes val titleRes: Int, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    object Dashboard : Screen("dashboard", R.string.tab_dashboard, Icons.Filled.Dashboard)
    object Control : Screen("control", R.string.tab_control, Icons.Filled.ControlCamera)
    object ThreeD : Screen("threed", R.string.tab_3d_view, Icons.Filled.ViewInAr)
    object Settings : Screen("settings", R.string.tab_settings, Icons.Filled.Settings)
}

val items = listOf(Screen.Dashboard, Screen.Control, Screen.ThreeD, Screen.Settings)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: RobotViewModel) {
    val navController = rememberNavController()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("HYDRA-UMC ANDROID CONTROL") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination
                items.forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = null) },
                        label = { Text(stringResource(screen.titleRes)) },
                        selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(navController, startDestination = Screen.Dashboard.route, Modifier.padding(innerPadding)) {
            composable(Screen.Dashboard.route) { DashboardScreen(viewModel) }
            composable(Screen.Control.route) { ControlScreen(viewModel) }
            composable(Screen.ThreeD.route) { ThreeDScreen(viewModel) }
            composable(Screen.Settings.route) { SettingsScreen(viewModel) }
        }
    }
}
