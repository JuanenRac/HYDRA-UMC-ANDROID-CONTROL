// =============================================================================
// HYDRA-UMC CONTROL - Configuration screen for managing connectivity settings
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0 - see LICENSE
// =============================================================================
package com.hydraumc.control.ui

import android.Manifest
import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Update
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.hydraumc.control.viewmodel.RobotViewModel
import com.hydraumc.control.viewmodel.AppUpdateState
import com.hydraumc.control.viewmodel.AppUpdateViewModel
import com.hydraumc.control.R
import com.hydraumc.control.model.BleDevice
import com.hydraumc.control.model.ServerInfo
import com.hydraumc.control.ui.theme.metallicIndustrial
import com.hydraumc.control.ui.theme.HydraButton

/**
 * Screen that handles Wi-Fi and Bluetooth connectivity settings.
 * 
 * @param viewModel The shared RobotViewModel.
 * @param onEnableBluetooth Callback to request Bluetooth activation.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun SettingsScreen(
    viewModel: RobotViewModel,
    updateViewModel: AppUpdateViewModel,
    onEnableBluetooth: () -> Unit = {},
) {
    /** Index of the currently selected settings tab. */
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val updateState by updateViewModel.state.collectAsState()
    /** List of tab items for Wi-Fi, Bluetooth and application updates. */
    val tabs = listOf(
        TabItem(stringResource(R.string.tab_wifi), Icons.Default.Wifi),
        TabItem(stringResource(R.string.tab_bluetooth), Icons.Default.Bluetooth),
        TabItem(stringResource(R.string.tab_updates), Icons.Default.Update),
    )

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = selectedTabIndex) {
            tabs.forEachIndexed { index, tab ->
                Tab(
                    selected = selectedTabIndex == index,
                    onClick = { selectedTabIndex = index },
                    text = { Text(tab.title) },
                    icon = { Icon(tab.icon, contentDescription = null) },
                )
            }
        }

        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            when (selectedTabIndex) {
                0 -> WifiSettings(viewModel)
                1 -> BluetoothSettings(viewModel, onEnableBluetooth)
                2 -> UpdateSettings(updateState, updateViewModel)
            }
        }
    }
}

/** 
 * Data class representing a tab item in the Settings screen.
 * @property title The display title of the tab.
 * @property icon The icon associated with the tab.
 */
data class TabItem(val title: String, val icon: ImageVector)

/** Settings page for the signed, operator-approved GitHub Release update path. */
@Composable
private fun UpdateSettings(state: AppUpdateState, updateViewModel: AppUpdateViewModel) {
    Column {
        Text(stringResource(R.string.app_updates), style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            stringResource(R.string.update_current_version, com.hydraumc.control.BuildConfig.VERSION_NAME),
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(modifier = Modifier.height(16.dp))
        when (state) {
            AppUpdateState.Idle, AppUpdateState.UpToDate -> {
                if (state is AppUpdateState.UpToDate) {
                    Text(stringResource(R.string.update_up_to_date), color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(8.dp))
                }
                HydraButton(
                    text = stringResource(R.string.update_check),
                    onClick = updateViewModel::checkForUpdate,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            AppUpdateState.Checking -> {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(8.dp))
                Text(stringResource(R.string.update_checking))
            }
            is AppUpdateState.Available -> UpdateAvailableContent(
                update = state.update,
                onDownload = { updateViewModel.downloadAndInstall(state.update) },
            )
            is AppUpdateState.Downloading -> {
                Text(stringResource(R.string.update_downloading, state.progress ?: 0))
                state.progress?.let { LinearProgressIndicator(progress = { it / 100f }, modifier = Modifier.fillMaxWidth()) }
            }
            is AppUpdateState.InstallPermissionRequired -> {
                Text(stringResource(R.string.update_install_permission))
                Spacer(modifier = Modifier.height(8.dp))
                HydraButton(
                    text = stringResource(R.string.update_allow_install),
                    onClick = updateViewModel::openInstallPermissionSettings,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            AppUpdateState.Installing -> Text(stringResource(R.string.update_installing))
            is AppUpdateState.Failed -> {
                Text(state.message, color = MaterialTheme.colorScheme.error)
                Spacer(modifier = Modifier.height(8.dp))
                HydraButton(
                    text = stringResource(R.string.update_check),
                    onClick = updateViewModel::checkForUpdate,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
fun UpdateAvailableContent(
    update: com.hydraumc.control.update.AvailableUpdate,
    onDownload: () -> Unit,
) {
    Text(stringResource(R.string.update_available, update.version.toString()), style = MaterialTheme.typography.titleMedium)
    if (update.notes.isNotBlank()) {
        Spacer(modifier = Modifier.height(8.dp))
        Text(stringResource(R.string.update_release_notes), style = MaterialTheme.typography.labelLarge)
        Text(update.notes, style = MaterialTheme.typography.bodySmall)
    }
    Spacer(modifier = Modifier.height(16.dp))
    HydraButton(
        text = stringResource(R.string.update_download_install),
        onClick = onDownload,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** 
 * Composable for managing Wi-Fi/Network connection settings.
 * @param viewModel The shared RobotViewModel.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WifiSettings(viewModel: RobotViewModel) {
    /** Local state for the target IP address input. */
    var ipAddress by remember { mutableStateOf(viewModel.ipAddress.value) }
    /** Local state for the target port input. */
    var port by remember { mutableStateOf(viewModel.port.value) }
    /** List of discovered HYDRA-UMC servers on the LAN. */
    val discoveredServers = viewModel.discoveredServers.value
    /** Flag indicating if a network scan is in progress. */
    val isScanning = viewModel.isScanning.value
    /** Last reported connection error message. */
    val lastError = viewModel.lastError.value

    Column {
        Text(stringResource(R.string.connection_settings), style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = ipAddress,
            onValueChange = { ipAddress = it },
            label = { Text(stringResource(R.string.ip_label)) },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = port,
            onValueChange = { port = it },
            label = { Text(stringResource(R.string.port_label)) },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(16.dp))
        HydraButton(
            text = stringResource(R.string.save_and_connect),
            onClick = {
                viewModel.ipAddress.value = ipAddress
                viewModel.port.value = port
                viewModel.connect()
            },
            modifier = Modifier.fillMaxWidth(),
        )

        if (lastError != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(lastError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        Spacer(modifier = Modifier.height(24.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.search_local), style = MaterialTheme.typography.titleMedium)
            if (isScanning) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                HydraButton(
                    text = stringResource(R.string.scan_button),
                    onClick = { viewModel.scanNetwork() }
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))

        if (discoveredServers.isEmpty() && !isScanning) {
            Text(stringResource(R.string.no_results_yet), style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        } else {
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(discoveredServers) { server ->
                    ServerCard(server) {
                        ipAddress = server.host
                        port = server.port.toString()
                        viewModel.connectToDiscovered(server)
                    }
                }
            }
        }
    }
}

/** 
 * UI component representing a discovered server in a list.
 * @param server The ServerInfo data.
 * @param onConnect Callback for when the user wants to connect to this server.
 */
@Composable
private fun ServerCard(server: ServerInfo, onConnect: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .metallicIndustrial()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(server.displayName, style = MaterialTheme.typography.titleSmall)
                Text(
                    stringResource(R.string.server_info, server.host, server.port, server.controllerCount, server.robotCount),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            HydraButton(
                text = stringResource(R.string.connect_button),
                onClick = onConnect
            )
        }
    }
}

/** 
 * Composable for managing Bluetooth settings and scanning.
 * @param viewModel The shared RobotViewModel.
 * @param onEnableBluetooth Callback to trigger system Bluetooth dialog.
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun BluetoothSettings(viewModel: RobotViewModel, onEnableBluetooth: () -> Unit) {
    /** Rationale: Determine required Bluetooth permissions based on Android version. */
    val bluetoothPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        listOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    /** State to manage multiple runtime permissions. */
    val permissionState = rememberMultiplePermissionsState(permissions = bluetoothPermissions)
    val discoveredDevices = viewModel.discoveredBtDevices.value
    val isBtScanning = viewModel.isBtScanning.value
    val isBtEnabled = viewModel.isBtEnabled.value
    val lastBtError = viewModel.lastBtError.value

    // One-shot refresh on entering this screen, for the case Bluetooth was
    // toggled while the app process wasn't alive to see the system
    // broadcast at all. While the app IS alive, isBtEnabled/lastBtError
    // already stay live via RobotViewModel's own BroadcastReceiver for
    // ACTION_STATE_CHANGED (registered once in its init{}, not scoped to
    // this screen) - this call is just a safety net, not the primary path.
    LaunchedEffect(Unit) {
        viewModel.refreshBtStatus()
    }

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.tab_bluetooth), style = MaterialTheme.typography.titleLarge)
            Switch(
                checked = isBtEnabled,
                onCheckedChange = { 
                    if (it) onEnableBluetooth() else viewModel.disconnectBle()
                }
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))

        if (isBtEnabled) {
            if (!permissionState.allPermissionsGranted) {
                BluetoothPermissionSection(permissionState)
            } else {
                BluetoothScanSection(viewModel, isBtScanning, discoveredDevices)
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .metallicIndustrial(backgroundColor = MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Bluetooth is physically disabled.", color = Color.White)
                    Spacer(modifier = Modifier.height(12.dp))
                    HydraButton(
                        text = "ENABLE SYSTEM BT",
                        onClick = onEnableBluetooth
                    )
                }
            }
        }

        if (lastBtError != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(lastBtError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** 
 * UI section to prompt the user for necessary Bluetooth permissions.
 * @param permissionState The permission state object.
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun BluetoothPermissionSection(permissionState: com.google.accompanist.permissions.MultiplePermissionsState) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .metallicIndustrial(backgroundColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column {
            Text(stringResource(R.string.bt_enable_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.bt_permission_required), style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(8.dp))
            HydraButton(
                text = stringResource(R.string.bt_grant_permission),
                onClick = { permissionState.launchMultiplePermissionRequest() },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** 
 * UI section for performing and displaying results of a Bluetooth scan.
 * @param viewModel The shared RobotViewModel.
 * @param isScanning Flag indicating if scanning is active.
 * @param devices List of discovered devices.
 */
@Composable
private fun BluetoothScanSection(
    viewModel: RobotViewModel,
    isScanning: Boolean,
    devices: List<BleDevice>
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.auto_discovery), style = MaterialTheme.typography.titleMedium)
            if (isScanning) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        if (!isScanning) {
            HydraButton(
                text = stringResource(R.string.bt_scan_button),
                onClick = { viewModel.scanBluetooth() },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            stringResource(R.string.bt_enable_description),
            style = MaterialTheme.typography.bodySmall
        )
        
        Spacer(modifier = Modifier.height(16.dp))

        if (devices.isEmpty() && !isScanning) {
            Text(stringResource(R.string.bt_no_devices), style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        } else {
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(devices) { device ->
                    DeviceCard(device) {
                        viewModel.connectBle(device)
                    }
                }
            }
        }
    }
}

/** 
 * UI component representing a discovered BLE device in a list.
 * @param device The BleDevice data.
 * @param onConnect Callback for when the user wants to connect via BLE.
 */
@Composable
private fun DeviceCard(device: BleDevice, onConnect: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .metallicIndustrial()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(device.displayName, style = MaterialTheme.typography.titleSmall)
                Text(
                    stringResource(R.string.bt_device_info, device.address, "${device.rssi} dBm"),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            HydraButton(
                text = stringResource(R.string.connect_button),
                onClick = onConnect
            )
        }
    }
}
