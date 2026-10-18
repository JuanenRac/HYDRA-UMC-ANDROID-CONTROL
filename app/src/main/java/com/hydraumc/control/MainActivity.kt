// =============================================================================
// HYDRA-UMC CONTROL - Main entry point activity for the application
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.RecognizerIntent
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
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

// A widget or notification STOP that arrives on a cold start has no server
// connection yet (the saved session is still being restored). It waits this
// long for the connection and then sends the global STOP anyway, so that a
// stop which cannot reach the server shows up as "not confirmed" instead of
// disappearing.
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

    // The widget's STOP intent can arrive during a fully cold start (app
    // process not alive), before the saved session has been restored and the
    // server connection exists. handleIntent() only flags the request; a
    // Compose LaunchedEffect (in setContent) sends the global STOP the moment
    // the connection is there, or after GLOBAL_ESTOP_TIMEOUT_MS regardless.
    // The global STOP needs no robot list, so nothing else is waited for.
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

    /**
     * Voice-assistant button (VoiceAssistantDialog): same real
     * ACTION_RECOGNIZE_SPEECH + RECORD_AUDIO pattern already proven in
     * HYDRA-UMC-WATCH's own MainActivity, ported here for this app's own
     * in-app assistant rather than a paired-Watch relay. A missing/denied
     * recognizer is surfaced through robotViewModel.lastError, not thrown -
     * the dialog stays open and usable either way.
     */
    private val recognizeSpeechLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode != RESULT_OK) return@registerForActivityResult
        val transcript = result.data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
            ?.trim()
            .orEmpty()
        if (transcript.isNotEmpty()) {
            robotViewModel.sendVoiceTurn(transcript)
        }
    }

    private val requestMicrophonePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            launchSpeechRecognition()
        } else {
            robotViewModel.lastError.value = getString(R.string.voice_permission_denied)
        }
    }

    private fun startVoiceRecognition() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestMicrophonePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        launchSpeechRecognition()
    }

    private fun launchSpeechRecognition() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PROMPT, getString(R.string.voice_prompt))
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        if (intent.resolveActivity(packageManager) == null) {
            robotViewModel.lastError.value = getString(R.string.voice_unavailable)
            return
        }
        recognizeSpeechLauncher.launch(intent)
    }

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

            // Deferred global STOP - see pendingGlobalEstop's own comment above.
            LaunchedEffect(pendingGlobalEstop) {
                if (pendingGlobalEstop) {
                    var waited = 0L
                    while (robotViewModel.apiClient == null && waited < GLOBAL_ESTOP_TIMEOUT_MS) {
                        delay(100)
                        waited += 100
                    }
                    pendingGlobalEstop = false
                    // With no connection this still records "STOP not confirmed".
                    robotViewModel.globalStop()
                }
            }

            var splashTimerDone by remember { mutableStateOf(value = false) }
            val isLoggedIn = robotViewModel.isLoggedIn.value
            // Real fix for a live-reproduced bug, not just belt-and-braces:
            // see RobotViewModel.authCheckComplete's own comment. Splash now
            // stays up until BOTH the branding timer AND the cached-session
            // check have finished, whichever takes longer - previously the
            // timer alone decided, so a slow authPrefs.loadAuth() could
            // still be resolving after LoginScreen was already showing and
            // being typed into.
            val showSplash = !splashTimerDone || !robotViewModel.authCheckComplete.value

            HydraTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    if (showSplash) {
                        CustomSplashScreen { splashTimerDone = true }
                    } else if (!isLoggedIn) {
                        LoginScreen(robotViewModel)
                    } else {
                        MainScreen(
                            robotViewModel,
                            appUpdateViewModel,
                            onEnableBluetooth = {
                                /** Intent to request enabling Bluetooth. */
                                val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                                enableBluetoothLauncher.launch(enableBtIntent)
                            },
                            onStartVoiceRecognition = ::startVoiceRecognition,
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        // A user may have just approved Android's per-app "install unknown
        // apps" setting. Resume only the verified APK already in our cache;
        // no update is downloaded or installed merely because the app opens.
        appUpdateViewModel.resumeInstallAfterPermissionApproval()
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == "ACTION_GLOBAL_ESTOP") {
            // Just flag it - the LaunchedEffect in setContent{} above sends the
            // global STOP once the connection exists. See pendingGlobalEstop's
            // own comment for why that matters on a cold start.
            pendingGlobalEstop = true
        }
    }
}
