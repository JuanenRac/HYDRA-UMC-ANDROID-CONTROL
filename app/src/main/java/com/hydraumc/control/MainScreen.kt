// =============================================================================
// HYDRA-UMC CONTROL - Main UI layout with navigation and top bar
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0 - see LICENSE
// =============================================================================
package com.hydraumc.control

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.material.icons.filled.CameraAlt
import com.hydraumc.control.ui.*
import com.hydraumc.control.ui.theme.metallicIndustrial
import com.hydraumc.control.viewmodel.RobotViewModel

/**
 * Sealed class defining the different screens available for navigation.
 * @property route The navigation route string.
 * @property titleRes The string resource ID for the tab label.
 * @property icon The icon representing the tab.
 */
sealed class Screen(val route: String, @StringRes val titleRes: Int, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    /** The Dashboard screen. */
    object Dashboard : Screen("dashboard", R.string.tab_dashboard, Icons.Filled.Dashboard)
    /** The Manual Control screen. */
    object Control : Screen("control", R.string.tab_control, Icons.Filled.ControlCamera)
    /** The Camera Feed screen. */
    object Camera : Screen("camera", R.string.tab_camera, Icons.Filled.CameraAlt)
    /** The 3D Simulation screen. */
    object ThreeD : Screen("threed", R.string.tab_3d_view, Icons.Filled.ViewInAr)
    /** The Application Settings screen. */
    object Settings : Screen("settings", R.string.tab_settings, Icons.Filled.Settings)
}

/** List of screen items to display in the Bottom Navigation Bar. */
val items = listOf(Screen.Dashboard, Screen.Control, Screen.Camera, Screen.ThreeD, Screen.Settings)

/**
 * Main application screen that provides the scaffold for navigation and global UI components.
 * @param viewModel The shared robot view model.
 * @param onEnableBluetooth Callback to request enabling Bluetooth.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: RobotViewModel, onEnableBluetooth: () -> Unit = {}) {
    /** The navigation controller for the entire app. */
    val navController = rememberNavController()
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { 
                    Box(
                        modifier = Modifier
                            .padding(vertical = 4.dp)
                            .metallicIndustrial(
                                backgroundColor = MaterialTheme.colorScheme.surfaceVariant,
                                borderColor = MaterialTheme.colorScheme.primary
                            )
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = buildAnnotatedString {
                                withStyle(style = SpanStyle(color = androidx.compose.ui.graphics.Color.White)) {
                                    append("HYDRA")
                                }
                                withStyle(style = SpanStyle(color = androidx.compose.ui.graphics.Color(0xFF00C853))) {
                                    append("-UM")
                                }
                                withStyle(style = SpanStyle(color = androidx.compose.ui.graphics.Color.Red)) {
                                    append("C")
                                }
                                withStyle(style = SpanStyle(color = androidx.compose.ui.graphics.Color.LightGray)) {
                                    append(" CONTROL")
                                }
                            },
                            style = MaterialTheme.typography.titleLarge.copy(
                                shadow = androidx.compose.ui.graphics.Shadow(
                                    color = androidx.compose.ui.graphics.Color.Black,
                                    offset = androidx.compose.ui.geometry.Offset(2f, 2f),
                                    blurRadius = 4f
                                ),
                                letterSpacing = 2.sp
                            ),
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.primary
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                /** Current navigation backstack entry to track selected tab. */
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                /** The current destination in the navigation graph. */
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
            composable(Screen.Camera.route) { CameraScreen(viewModel) }
            composable(Screen.ThreeD.route) { ThreeDScreen(viewModel) }
            composable(Screen.Settings.route) { SettingsScreen(viewModel, onEnableBluetooth) }
        }
    }
}
