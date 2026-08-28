// =============================================================================
// HYDRA-UMC CONTROL - Main entry point activity for the application
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0 - see LICENSE
// =============================================================================
package com.hydraumc.control

import android.os.Build
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.net.wifi.WifiManager
import androidx.activity.result.contract.ActivityResultContracts
import com.hydraumc.control.ui.CustomSplashScreen
import com.hydraumc.control.ui.LoginScreen
import com.hydraumc.control.ui.theme.HydraTheme
import com.hydraumc.control.viewmodel.RobotViewModel
import com.hydraumc.control.viewmodel.AppUpdateViewModel
import kotlinx.coroutines.delay

// A widget-triggered E-STOP with no robot roster yet (cold start, no cache,
// server unreachable) used to leave pendingGlobalEstop set forever with no
// feedback - see the LaunchedEffect pair in setContent{} below. Bounds how
// long it waits for robots.value to populate before giving up and telling
// the user instead of hanging silently.
private const val GLOBAL_ESTOP_TIMEOUT_MS = 15_000L

/**
 * Main activity that initializes the application, handles the splash screen,
 * and sets up the primary Compose UI content.
 */
class MainActivity : FragmentActivity() {
    /** The shared ViewModel that manages robot state and connectivity. */
    private val robotViewModel: RobotViewModel by viewModels()
    /** Checks only public GitHub Release metadata when the app starts. */
    private val appUpdateViewModel: AppUpdateViewModel by viewModels()

    // The widget's E-STOP intent can arrive during a fully cold start (app
    // process not already alive), when robotViewModel.robots.value is still
    // empty - it only gets populated later, either asynchronously from the
    // cached state the ViewModel's own init{} loads (StateCache) or from a
    // live scanNetwork()/connect(). handleIntent() itself only flags the
    // request; the actual stop loop lives in a Compose LaunchedEffect (below,
    // in setContent) that waits for robots.value to actually have entries
    // before acting, so a cold-start E-STOP still reliably stops every robot
    // once the roster loads instead of firing once against an empty list -
    // exactly the scenario a home-screen safety widget needs to survive.
    private var pendingGlobalEstop by mutableStateOf(false)

    /** Activity result launcher for enabling Bluetooth. */
    private val enableBluetoothLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            robotViewModel.scanBluetooth()
        }
    }

    // A manifest <uses-permission> declaration alone never grants a dangerous
    // permission (API 23+) - the user has to actually approve a runtime
    // prompt. Without one, two things silently degrade with no error the
    // user can act on: NsdManager.discoverServices() in network/Discovery.kt
    // (API 33+ requires ACCESS_FINE_LOCATION or NEARBY_WIFI_DEVICES to
    // actually be GRANTED, not just declared, or it just reports zero
    // services - the subnet-scan half of discovery still works either way,
    // it doesn't need this), and scanBluetooth() in RobotViewModel.kt
    // (BLUETOOTH_SCAN/CONNECT on API 31+, ACCESS_FINE_LOCATION on older
    // ones). Requested once up front here so both have a real chance of
    // being granted instead of failing quietly forever.
    private val requestDiscoveryPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { /* No explicit handling needed: a denial just means NSD/BLE stay degraded
          to what they already have (the subnet scan for network discovery,
          nothing for Bluetooth) - the user can grant them later from Android's
          own App Info > Permissions screen and retry from Settings. */ }

    /** Every runtime permission NSD (mDNS) discovery, Bluetooth LE scanning, and notifications need, for this device's API level. */
    private fun discoveryPermissionsToRequest(): Array<String> {
        val permissions = mutableListOf(
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_COARSE_LOCATION,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(android.Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(android.Manifest.permission.BLUETOOTH_CONNECT)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add("android.permission.NEARBY_WIFI_DEVICES")
            // Declared in AndroidManifest.xml since this app's earliest BLE
            // scaffolding, but - like every dangerous permission since API
            // 23 - a manifest declaration alone never grants it; nothing in
            // this app requested it at runtime until now, so the persistent
            // E-STOP notification (NotificationHelper.showSafetyNotification,
            // shown on every WS connect) almost certainly never actually
            // appeared on Android 13+ devices, with no indication to the
            // user that their quick-E-STOP notification was silently absent.
            permissions.add(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        return permissions.toTypedArray()
    }

    /**
     * Called when the activity is starting. This is where most initialization should go.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        /** Flag to control the native splash screen visibility. */
        var keepNativeSplash = true
        splashScreen.setKeepOnScreenCondition { keepNativeSplash }
        
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)

        // Ask up front, not lazily on first use - see the launcher's own
        // comment above for why a manifest declaration alone isn't enough.
        requestDiscoveryPermissionsLauncher.launch(discoveryPermissionsToRequest())

        // Auto-enable WiFi and search for servers on startup
        try {
            val wifiManager = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
            if (!wifiManager.isWifiEnabled) {
                // Rationale: Modern Android (Q+) doesn't allow setWifiEnabled(true).
                // We attempt it as a best-effort for older industrial tablets, 
                // but discovery will still prompt the user if needed.
                @Suppress("DEPRECATION")
                wifiManager.isWifiEnabled = true
            }
        } catch (_: Exception) {
            // fail silently if permissions are missing for this action
        }

        setContent {
            // Remove native splash right away to show our custom Compose splash screen
            LaunchedEffect(Unit) {
                keepNativeSplash = false
                // Initial scan
                robotViewModel.scanNetwork()
                appUpdateViewModel.checkForUpdate()
            }

            // Deferred global E-STOP - see pendingGlobalEstop's own comment above.
            // Re-runs whenever robots.value changes, so a cold start (robots.value
            // starts empty, gets populated moments later from cache/network) still
            // reliably stops every robot once there's actually something to stop.
            val robotsForEstop = robotViewModel.robots.value
            LaunchedEffect(pendingGlobalEstop, robotsForEstop) {
                if (pendingGlobalEstop && robotsForEstop.isNotEmpty()) {
                    robotsForEstop.forEach { robot ->
                        robotViewModel.selectedRobotId.value = robot.id
                        robotViewModel.sendCommand("stop")
                    }
                    pendingGlobalEstop = false
                }
            }

            // Companion timeout for the LaunchedEffect above - if
            // robotsForEstop is STILL empty this long after the widget tap
            // (no cache, server unreachable, robot on another network),
            // stop waiting and tell the user instead of leaving
            // pendingGlobalEstop set forever with no feedback at all. Not a
            // full fix (a real fix needs a Service/WorkManager path that
            // doesn't depend on the UI or a live robots.value at all - see
            // mejoras_futuras.txt), but an operator who taps the E-STOP
            // widget and gets nothing at least now finds out their tap
            // didn't reach any robot, rather than assuming it did.
            LaunchedEffect(pendingGlobalEstop) {
                if (pendingGlobalEstop) {
                    delay(GLOBAL_ESTOP_TIMEOUT_MS)
                    if (pendingGlobalEstop && robotViewModel.robots.value.isEmpty()) {
                        pendingGlobalEstop = false
                        robotViewModel.lastError.value = getString(R.string.error_global_estop_no_robots)
                    }
                }
            }

            var showSplash by remember { mutableStateOf(value = true) }
            val isLoggedIn = robotViewModel.isLoggedIn.value

            HydraTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    if (showSplash) {
                        CustomSplashScreen { showSplash = false }
                    } else if (!isLoggedIn) {
                        LoginScreen(robotViewModel)
                    } else {
                        MainScreen(robotViewModel, appUpdateViewModel) {
                            /** Intent to request enabling Bluetooth. */
                            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                            enableBluetoothLauncher.launch(enableBtIntent)
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == "ACTION_GLOBAL_ESTOP") {
            // Just flag it - the actual stop loop runs from the LaunchedEffect in
            // setContent{} above once robots.value is real, not empty. See
            // pendingGlobalEstop's own comment for why that matters on a cold start.
            pendingGlobalEstop = true
        }
    }
}
