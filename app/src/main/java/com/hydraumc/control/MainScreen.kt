// =============================================================================
// HYDRA-UMC CONTROL - Main UI layout with navigation and top bar
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control

import android.content.res.Configuration
import com.hydraumc.control.ui.LocalStopViewModel
import com.hydraumc.control.ui.StopAlertDialog
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import com.hydraumc.control.ui.theme.HydraColors
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.hydraumc.control.R
import com.hydraumc.control.ui.*
import com.hydraumc.control.viewmodel.RobotViewModel
import com.hydraumc.control.viewmodel.AppUpdateState
import com.hydraumc.control.viewmodel.AppUpdateViewModel

/**
 * The screens of the app.
 * @property route The navigation route string.
 * @property titleRes The string resource ID for the label.
 * @property icon The icon representing the screen.
 */
sealed class Screen(val route: String, @param:StringRes val titleRes: Int, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    /** The home screen: the state of the cell. */
    object Dashboard : Screen("dashboard", R.string.tab_home, Icons.Filled.Home)
    /** The Manual Control screen. */
    object Control : Screen("control", R.string.tab_control, Icons.Filled.ControlCamera)
    /** The Camera Feed screen. */
    object Camera : Screen("camera", R.string.tab_cameras, Icons.Filled.Videocam)
    /** What happened, when and what to do. */
    object Alerts : Screen("notices", R.string.tab_notices, Icons.Filled.Notifications)
    /** The tools and settings, as explained cards. */
    object More : Screen("more", R.string.tab_more, Icons.Filled.Apps)
    /** The 3D Simulation screen (from More or Control). */
    object ThreeD : Screen("threed", R.string.tab_3d_view, Icons.Filled.ViewInAr)
    /** The Industrial Telemetry screen (from More). */
    object Telemetry : Screen("telemetry", R.string.tab_telemetry, Icons.Filled.Terminal)
    /** Help by screen and level (from the header and More). */
    object Help : Screen("help", R.string.help_title, Icons.Filled.Info)
    /** The Application Settings screen (from More). */
    object Settings : Screen("settings", R.string.tab_settings, Icons.Filled.Settings)
}

/** List of screen items to display in the Bottom Navigation Bar. */
val items = listOf(Screen.Dashboard, Screen.Control, Screen.Camera, Screen.Alerts, Screen.More)

/** The screens reached from More: the bar keeps More selected while one of them is open. */
private val moreRoutes = setOf(Screen.ThreeD.route, Screen.Telemetry.route, Screen.Settings.route, Screen.Help.route)

/**
 * Main application screen that provides the scaffold for navigation and global UI components.
 * @param viewModel The shared robot view model.
 * @param onEnableBluetooth Callback to request enabling Bluetooth.
 */
@Composable
fun MainScreen(
    viewModel: RobotViewModel,
    updateViewModel: AppUpdateViewModel,
    onEnableBluetooth: () -> Unit = {},
    onStartVoiceRecognition: () -> Unit = {},
) {
    // Every dialog and menu below carries the global STOP of this view model (ui/StopWindows.kt).
    CompositionLocalProvider(LocalStopViewModel provides viewModel) {
        MainScreenContent(viewModel, updateViewModel, onEnableBluetooth, onStartVoiceRecognition)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScreenContent(
    viewModel: RobotViewModel,
    updateViewModel: AppUpdateViewModel,
    onEnableBluetooth: () -> Unit,
    onStartVoiceRecognition: () -> Unit,
) {
    /** The navigation controller for the entire app. */
    val navController = rememberNavController()
    var showAboutDialog by remember { mutableStateOf(value = false) }
    var showProfileDialog by remember { mutableStateOf(value = false) }
    var showVoiceAssistantDialog by remember { mutableStateOf(value = false) }
    val updateState by updateViewModel.state.collectAsState()
    val unseen = com.hydraumc.control.util.Notices.unseen(viewModel.notices.value)
    // The notices screen is filled from what the phone sees while the app is on screen.
    LaunchedEffect(viewModel) {
        while (true) {
            viewModel.noticesTick()
            kotlinx.coroutines.delay(1_000)
        }
    }
    LaunchedEffect(updateState) {
        (updateState as? AppUpdateState.Available)?.let { viewModel.noteUpdate(it.update.version.toString()) }
    }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    // Hoisted above the bottomBar's own read of the same state (review
    // note: "modo landscape optimizado para tablets" -
    // BUG (found while
    // implementing that idea): landscape must not unconditionally force
    // the fullscreen 3D view regardless of which tab is open, or a
    // tablet (landscape being its natural default orientation, unlike a
    // phone) rotating away from portrait while on Dashboard/Control/
    // Camera/Telemetry/Settings got hijacked to 3D with no way back
    // except rotating back to portrait. Fullscreen 3D-in-landscape is
    // still genuinely good UX for the one case it was actually meant
    // for (already viewing the 3D tab, phone on its side, immersive
    // view) - narrowed to only that case instead of removed outright.
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val isOnThreeDScreen = currentDestination?.hierarchy?.any { it.route == Screen.ThreeD.route } == true

    if (isLandscape && isOnThreeDScreen) {
        // Full screen 3D in landscape mode
        Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
            Box(Modifier.fillMaxSize()) {
                ThreeDScreen(viewModel)
                // ThreeDScreen carries its own STOP button; the result of the last one shows here.
                StopAllBanner(viewModel, Modifier.align(Alignment.TopCenter))
            }
        }
        return
    }

    if (showAboutDialog) {
        AboutDialog { showAboutDialog = false }
    }
    
    if (showProfileDialog) {
        UserProfileDialog(viewModel = viewModel, onDismiss = { showProfileDialog = false })
    }

    if (showVoiceAssistantDialog) {
        VoiceAssistantDialog(
            viewModel = viewModel,
            onStartVoiceRecognition = onStartVoiceRecognition,
            onDismiss = { showVoiceAssistantDialog = false },
        )
    }

    // A tab keeps what was on it; More always opens its list of cards.
    val openTab: (Screen) -> Unit = { screen ->
        navController.navigate(screen.route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = screen != Screen.More
        }
    }
    // A screen of More is opened above it: Back returns to where it was asked from.
    val openAbove: (Screen) -> Unit = { screen -> navController.navigate(screen.route) { launchSingleTop = true } }
    var settingsTab by rememberSaveable { mutableIntStateOf(SettingsTab.CONNECTION) }
    val openSettings: (Int) -> Unit = { tab ->
        settingsTab = tab
        openAbove(Screen.Settings)
    }

    // The help opens on the topic of the screen it is asked from.
    var helpTopic by rememberSaveable { mutableStateOf<HelpTopic?>(null) }
    val openHelp: () -> Unit = {
        if (currentDestination?.route != Screen.Help.route) {
            helpTopic = when (currentDestination?.route) {
                Screen.Dashboard.route -> HelpTopic.HOME
                Screen.Control.route, Screen.ThreeD.route -> HelpTopic.CONTROL
                Screen.Camera.route -> HelpTopic.CAMERAS
                Screen.Alerts.route -> HelpTopic.NOTICES
                Screen.Settings.route -> HelpTopic.ACCOUNT
                else -> null
            }
            openAbove(Screen.Help)
        }
    }

    // Startup checks only fetch release metadata. The actual APK transfer and
    // Android installer are both initiated by the operator from this prompt.
    if (updateState is AppUpdateState.Available) {
        val update = (updateState as AppUpdateState.Available).update
        StopAlertDialog(
            onDismissRequest = updateViewModel::dismiss,
            title = { Text(stringResource(R.string.app_updates)) },
            text = {
                UpdateAvailableContent(update) {
                    // Starting the download makes the dialog vanish (its
                    // own visibility condition stops matching the instant
                    // state leaves Available), which would drop the
                    // operator back on whatever screen was underneath with
                    // no way to see download/install progress. So the tap
                    // also navigates to Settings, which opens directly on its
                    // own Updates tab for exactly this in-flight state (see
                    // SettingsScreen.kt's own initialTabIndex comment).
                    updateViewModel.downloadAndInstall(update)
                    openSettings(SettingsTab.UPDATES)
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = updateViewModel::dismiss) {
                    Text(stringResource(R.string.update_later))
                }
            },
        )
    }

    Scaffold(
        topBar = {
            Column {
                HydraTopBar(viewModel, onVoice = { showVoiceAssistantDialog = true }, onProfile = { showProfileDialog = true }, onHelp = openHelp)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                StopAllBanner(viewModel)
            }
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
                // currentDestination is hoisted above: one source of truth for this bar and the landscape check.
                items.forEach { screen ->
                    val selected = currentDestination?.hierarchy?.any {
                        it.route == screen.route || (screen == Screen.More && it.route in moreRoutes)
                    } == true
                    NavigationBarItem(
                        modifier = Modifier.testTag("tab-" + screen.route),
                        icon = {
                            if (screen == Screen.Alerts && unseen > 0) {
                                BadgedBox(badge = { Badge { Text(unseen.toString()) } }) { Icon(screen.icon, contentDescription = null) }
                            } else {
                                Icon(screen.icon, contentDescription = null)
                            }
                        },
                        label = {
                            Text(text = stringResource(screen.titleRes), style = MaterialTheme.typography.labelSmall, maxLines = 1, softWrap = false)
                        },
                        selected = selected,
                        onClick = { openTab(screen) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = HydraColors.Accent,
                            selectedTextColor = HydraColors.Accent,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            unselectedIconColor = HydraColors.Muted,
                            unselectedTextColor = HydraColors.Muted,
                        ),
                    )
                }
            }
        }
    ) { innerPadding ->
        // fillMaxSize() is required here, not optional - without it NavHost
        // sizes itself to its own content's intrinsic height instead of
        // filling Scaffold's actual content slot. Every other tab
        // (Dashboard/Control/Camera/Telemetry/Settings) is a Column/
        // LazyColumn that happily self-sizes and LOOKS fine either way, so
        // this went unnoticed - but ThreeDScreen's WebView uses its own
        // fillMaxSize() internally, which fills whatever (possibly
        // zero-height) box NavHost itself ends up with. That's the real
        // cause of the 3D viewport's "367x0"/blank-black-screen bug: the
        // WebView's page (STUDIO's own React app) was correctly measuring
        // a genuinely zero-height Android View, not failing to render.
        Column(Modifier.padding(innerPadding).fillMaxSize()) {
        if (viewModel.isShowingStaleData) {
            val savedAt = viewModel.stateAtMs.value
            val time = savedAt?.let { java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date(it)) }
            Text(
                text = if (time != null) stringResource(R.string.stale_data_banner, time) else stringResource(R.string.stale_data_banner_unknown),
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }
        viewModel.plainRemoteHost.value?.let { host ->
            Text(
                text = stringResource(R.string.tls_warning_plain_remote, host),
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }
        NavHost(navController, startDestination = Screen.Dashboard.route, Modifier.weight(1f).fillMaxWidth()) {
            composable(Screen.Dashboard.route) {
                DashboardScreen(viewModel, onOpenControl = { openTab(Screen.Control) }, onOpenSettings = { openSettings(SettingsTab.CONNECTION) }, onOpenSecurity = { openSettings(SettingsTab.SECURITY) }, onOpenNotices = { openTab(Screen.Alerts) })
            }
            composable(Screen.Control.route) { ControlScreen(viewModel, onOpenThreeD = { openAbove(Screen.ThreeD) }) }
            composable(Screen.Camera.route) { CameraScreen(viewModel) }
            composable(Screen.ThreeD.route) { ThreeDScreen(viewModel) }
            composable(Screen.Telemetry.route) { TelemetryScreen(viewModel) }
            composable(Screen.Settings.route) { SettingsScreen(viewModel, updateViewModel, onEnableBluetooth, openTab = settingsTab) }
            composable(Screen.Alerts.route) {
                NoticesScreen(
                    viewModel.notices.value, onMarkSeen = viewModel::markNoticesSeen, onSettings = { openSettings(SettingsTab.NOTICES) },
                    server = com.hydraumc.control.ui.ServerRecordUi(viewModel.serverRecord.reading, viewModel.serverRecord.busy.value) { viewModel.serverRecord.refresh() },
                )
            }
            composable(Screen.Help.route) {
                HelpScreen(helpTopic) { topic ->
                    when (topic) {
                        HelpTopic.HOME -> openTab(Screen.Dashboard)
                        HelpTopic.CONTROL -> openTab(Screen.Control)
                        HelpTopic.CAMERAS -> openTab(Screen.Camera)
                        HelpTopic.NOTICES -> openTab(Screen.Alerts)
                        HelpTopic.ACCOUNT -> openSettings(SettingsTab.SECURITY)
                        else -> Unit
                    }
                }
            }
            composable(Screen.More.route) {
                MoreScreen(
                    onThreeD = { openAbove(Screen.ThreeD) },
                    onTelemetry = { openAbove(Screen.Telemetry) },
                    onSettings = openSettings,
                    onAccount = { showProfileDialog = true },
                    onHelp = {
                        helpTopic = null
                        openAbove(Screen.Help)
                    },
                    onAbout = { showAboutDialog = true },
                )
            }
        }
        }
    }
}
