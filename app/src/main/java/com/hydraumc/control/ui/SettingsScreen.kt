package com.hydraumc.control.ui

import android.Manifest
import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
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
import com.hydraumc.control.R
import com.hydraumc.control.model.BleDevice
import com.hydraumc.control.model.ServerInfo
import com.hydraumc.control.ui.theme.metallicIndustrial
import com.hydraumc.control.ui.theme.HydraButton
import com.hydraumc.control.ui.theme.IndustrialDanger

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun SettingsScreen(viewModel: RobotViewModel, onEnableBluetooth: () -> Unit = {}) {
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf(
        TabItem(stringResource(R.string.tab_wifi), Icons.Default.Wifi),
        TabItem(stringResource(R.string.tab_bluetooth), Icons.Default.Bluetooth),
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
            }
        }
    }
}

data class TabItem(val title: String, val icon: ImageVector)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WifiSettings(viewModel: RobotViewModel) {
    var ipAddress by remember { mutableStateOf(viewModel.ipAddress.value) }
    var port by remember { mutableStateOf(viewModel.port.value) }
    val discoveredServers = viewModel.discoveredServers.value
    val isScanning = viewModel.isScanning.value
    val lastError = viewModel.lastError.value

    Column {
        Text(stringResource(R.string.connection_settings), style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = ipAddress,
            onValueChange = { ipAddress = it },
            label = { Text(stringResource(R.string.ip_label)) },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = port,
            onValueChange = { port = it },
            label = { Text(stringResource(R.string.port_label)) },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))
        HydraButton(
            text = stringResource(R.string.save_and_connect),
            onClick = {
                viewModel.ipAddress.value = ipAddress
                viewModel.port.value = port
                viewModel.connect()
            },
            modifier = Modifier.fillMaxWidth()
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
            verticalAlignment = Alignment.CenterVertically
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
            verticalAlignment = Alignment.CenterVertically
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

@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun BluetoothSettings(viewModel: RobotViewModel, onEnableBluetooth: () -> Unit) {
    var isBtFeatureEnabled by remember { mutableStateOf(true) }
    
    val bluetoothPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        listOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    val permissionState = rememberMultiplePermissionsState(permissions = bluetoothPermissions)
    val discoveredDevices = viewModel.discoveredBtDevices.value
    val isBtScanning = viewModel.isBtScanning.value
    val isBtEnabled = viewModel.isBtEnabled.value
    val lastError = viewModel.lastError.value

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.tab_bluetooth), style = MaterialTheme.typography.titleLarge)
            Switch(
                checked = isBtFeatureEnabled,
                onCheckedChange = { 
                    isBtFeatureEnabled = it 
                    if (!it) viewModel.disconnectBle()
                }
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))

        if (isBtFeatureEnabled) {
            if (!permissionState.allPermissionsGranted) {
                BluetoothPermissionSection(permissionState)
            } else if (!isBtEnabled) {
                BluetoothEnableSection(onEnableBluetooth)
            } else {
                BluetoothScanSection(viewModel, isBtScanning, discoveredDevices)
            }
        } else {
            Text(
                "Bluetooth feature is disabled. Enable it to search for nearby robots.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray
            )
        }

        if (lastError != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(lastError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun BluetoothEnableSection(onEnableBluetooth: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(stringResource(R.string.bt_enable_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.bt_enable_description), style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(8.dp))
            HydraButton(
                text = stringResource(R.string.bt_enable_button),
                onClick = onEnableBluetooth,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

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
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun BluetoothScanSection(
    viewModel: RobotViewModel,
    isScanning: Boolean,
    devices: List<BleDevice>
) {
    val connectionStatus = viewModel.connectionStatus.value
    val isConnected = connectionStatus == stringResource(R.string.status_connected)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(stringResource(R.string.auto_discovery), style = MaterialTheme.typography.titleMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (isConnected) {
                HydraButton(
                    text = stringResource(R.string.bt_disconnect_button),
                    onClick = { viewModel.disconnectBle() },
                    backgroundColor = IndustrialDanger
                )
            }
            if (isScanning) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else if (!isConnected) {
                HydraButton(
                    text = stringResource(R.string.bt_scan_button),
                    onClick = { viewModel.scanBluetooth() }
                )
            }
        }
    }
    
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
            verticalAlignment = Alignment.CenterVertically
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
