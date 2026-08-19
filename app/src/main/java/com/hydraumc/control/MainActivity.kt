// =============================================================================
// HYDRA-UMC CONTROL - Main entry point activity for the application
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0 - see LICENSE
// =============================================================================
package com.hydraumc.control

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

/**
 * Main activity that initializes the application, handles the splash screen,
 * and sets up the primary Compose UI content.
 */
class MainActivity : FragmentActivity() {
    /** The shared ViewModel that manages robot state and connectivity. */
    private val robotViewModel: RobotViewModel by viewModels()

    // Found 2026-08-19 (SONNET/HYDRA-UMC-ANDROID-CONTROL/auditoria_historial.txt):
    // handleIntent() used to run in onCreate() BEFORE setContent{} even mounts,
    // and robotViewModel.robots.value only ever gets populated later, either
    // asynchronously from the cached state the ViewModel's own init{} loads
    // (StateCache) or from a live scanNetwork()/connect(). If the app process
    // wasn't already alive (a fully cold start from the home-screen widget,
    // GlobalStopWidget.kt), the E-STOP loop below ran over an EMPTY list and
    // silently stopped nothing - the exact scenario a home-screen safety
    // widget exists for. Fixed by deferring the actual stop loop into a
    // Compose LaunchedEffect (below, in setContent) that waits for
    // robots.value to actually have entries instead of firing once,
    // synchronously, against whatever (possibly nothing) is loaded yet.
    private var pendingGlobalEstop by mutableStateOf(false)

    /** Activity result launcher for enabling Bluetooth. */
    private val enableBluetoothLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            robotViewModel.scanBluetooth()
        }
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
            }

            // Deferred global E-STOP - see pendingGlobalEstop's own comment above.
            // Re-runs whenever robots.value changes, so a cold start (robots.value
            // starts empty, gets populated moments later from cache/network) still
            // reliably stops every robot once there's actually something to stop,
            // instead of firing once against an empty list and giving up.
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
                        MainScreen(robotViewModel) {
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
            // pendingGlobalEstop's own comment for why this used to fail cold.
            pendingGlobalEstop = true
        }
    }
}
