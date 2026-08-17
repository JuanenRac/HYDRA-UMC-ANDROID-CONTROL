// =============================================================================
// HYDRA-UMC CONTROL - Main UI layout with navigation and top bar
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0 - see LICENSE
// =============================================================================
package com.hydraumc.control

import android.content.res.Configuration
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
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
import com.hydraumc.control.ui.*
import com.hydraumc.control.ui.theme.metallicIndustrial
import com.hydraumc.control.viewmodel.RobotViewModel

/**
 * Sealed class defining the different screens available for navigation.
 * @property route The navigation route string.
 * @property titleRes The string resource ID for the tab label.
 * @property icon The icon representing the tab.
 */
sealed class Screen(val route: String, @param:StringRes val titleRes: Int, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    /** The Dashboard screen. */
    object Dashboard : Screen("dashboard", R.string.tab_dashboard, Icons.Filled.Dashboard)
    /** The Manual Control screen. */
    object Control : Screen("control", R.string.tab_control, Icons.Filled.ControlCamera)
    /** The Camera Feed screen. */
    object Camera : Screen("camera", R.string.tab_camera, Icons.Filled.CameraAlt)
    /** The 3D Simulation screen. */
    object ThreeD : Screen("threed", R.string.tab_3d_view, Icons.Filled.ViewInAr)
    /** The Industrial Telemetry screen. */
    object Telemetry : Screen("telemetry", R.string.tab_telemetry, Icons.Filled.Terminal)
    /** The Application Settings screen. */
    object Settings : Screen("settings", R.string.tab_settings, Icons.Filled.Settings)
}

/** List of screen items to display in the Bottom Navigation Bar. */
val items = listOf(Screen.Dashboard, Screen.Control, Screen.Camera, Screen.ThreeD)

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
    var showAboutDialog by remember { mutableStateOf(value = false) }
    var showProfileDialog by remember { mutableStateOf(value = false) }
    var serverDropdownExpanded by remember { mutableStateOf(value = false) }
    val discoveredServers = viewModel.discoveredServers.value

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    if (isLandscape) {
        // Full screen 3D in landscape mode
        Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
            ThreeDScreen(viewModel)
        }
        return
    }

    if (showAboutDialog) {
        AboutDialog { showAboutDialog = false }
    }
    
    if (showProfileDialog) {
        UserProfileDialog(viewModel = viewModel, onDismiss = { showProfileDialog = false })
    }

    Scaffold(
        topBar = {
            Column {
                CenterAlignedTopAppBar(
                    title = { 
                        Box(
                            modifier = Modifier
                                .padding(vertical = 4.dp)
                                .metallicIndustrial(
                                    backgroundColor = MaterialTheme.colorScheme.surfaceVariant,
                                    borderColor = MaterialTheme.colorScheme.primary,
                                )
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = buildAnnotatedString {
                                    withStyle(style = SpanStyle(color = Color.White)) {
                                        append("HYDRA")
                                    }
                                    withStyle(style = SpanStyle(color = Color(0xFF00C853))) {
                                        append("-UM")
                                    }
                                    withStyle(style = SpanStyle(color = Color.Red)) {
                                        append("C")
                                    }
                                    withStyle(style = SpanStyle(color = Color.LightGray)) {
                                        append(" CONTROL")
                                    }
                                },
                                style = MaterialTheme.typography.titleLarge.copy(
                                    shadow = androidx.compose.ui.graphics.Shadow(
                                        color = Color.Black,
                                        offset = androidx.compose.ui.geometry.Offset(2f, 2f),
                                        blurRadius = 4f,
                                    ),
                                    letterSpacing = 2.sp,
                                ),
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold,
                            )
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.primary,
                    )
                )

                // Sub-header Row with Server Selector and Icons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Server Selector (Left)
                    Box {
                        OutlinedButton(
                            onClick = { serverDropdownExpanded = true },
                            modifier = Modifier.height(40.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            Icon(Icons.Default.Dns, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (discoveredServers.isNotEmpty()) "Servers (${discoveredServers.size})" else "No Servers",
                                style = MaterialTheme.typography.labelMedium
                            )
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                        }

                        DropdownMenu(
                            expanded = serverDropdownExpanded,
                            onDismissRequest = { serverDropdownExpanded = false }
                        ) {
                            if (discoveredServers.isEmpty()) {
                                DropdownMenuItem(
                                    text = { Text("No se encontraron servidores") },
                                    onClick = { serverDropdownExpanded = false }
                                )
                            } else {
                                discoveredServers.forEach { server ->
                                    DropdownMenuItem(
                                        text = { Text("${server.displayName} (${server.host})") },
                                        onClick = {
                                            viewModel.connectToDiscovered(server)
                                            serverDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Icons (Right)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { showProfileDialog = true }) {
                            Icon(Icons.Default.Person, contentDescription = "Usuario", tint = MaterialTheme.colorScheme.primary)
                        }
                        IconButton(
                            onClick = { 
                                navController.navigate(Screen.Telemetry.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        ) {
                            Icon(Icons.Default.Terminal, contentDescription = "Telemetría", tint = MaterialTheme.colorScheme.primary)
                        }
                        IconButton(
                            onClick = { 
                                navController.navigate(Screen.Settings.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        ) {
                            Icon(Icons.Default.Settings, contentDescription = "Ajustes", tint = MaterialTheme.colorScheme.primary)
                        }
                        IconButton(onClick = { showAboutDialog = true }) {
                            Icon(Icons.Default.Info, contentDescription = "Acerca de", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
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
                        label = { 
                            Text(
                                text = stringResource(screen.titleRes),
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                softWrap = false
                            ) 
                        },
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
            composable(Screen.Telemetry.route) { TelemetryScreen(viewModel) }
            composable(Screen.Settings.route) { SettingsScreen(viewModel, onEnableBluetooth) }
        }
    }
}
