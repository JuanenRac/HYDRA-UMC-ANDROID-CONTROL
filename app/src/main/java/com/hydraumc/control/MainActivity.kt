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
import android.content.Context
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

        // Handle Global E-STOP from notification
        if (intent?.action == "ACTION_GLOBAL_ESTOP") {
            robotViewModel.robots.value.forEach { robot ->
                robotViewModel.selectedRobotId.value = robot.id
                robotViewModel.sendCommand("stop")
            }
        }

        enableEdgeToEdge()
        
        // Auto-enable WiFi and search for servers on startup
        try {
            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            if (!wifiManager.isWifiEnabled) {
                // Rationale: Modern Android (Q+) doesn't allow setWifiEnabled(true).
                // We attempt it as a best-effort for older industrial tablets, 
                // but discovery will still prompt the user if needed.
                @Suppress("DEPRECATION")
                wifiManager.isWifiEnabled = true
            }
        } catch (e: Exception) {
            // fail silently if permissions are missing for this action
        }

        setContent {
            // Remove native splash right away to show our custom Compose splash screen
            LaunchedEffect(Unit) {
                keepNativeSplash = false
                // Initial scan
                robotViewModel.scanNetwork()
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
}
