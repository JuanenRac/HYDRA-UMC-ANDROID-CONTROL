// =============================================================================
// HYDRA-UMC CONTROL - Primary ViewModel managing robot state and connectivity
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0 - see LICENSE
//
// Holds one HydraState mirror (model/HydraState.kt) and keeps it in sync
// with a HYDRA-UMC STUDIO server in both directions, following the exact
// contract in HYDRA-UMC-STUDIO/docs/REMOTE_API.md: GET/POST /api/settings
// for the read-modify-write cycle, GET /api/hydra-info for discovery, and
// /ws for live push - the server broadcasts every change (from ANY client)
// to every connected client. Mirrors HYDRA-UMC SUITE's own
// hydra_suite/net/client.py HydraConnection in spirit: every mutating
// action here (jog, speed, tool change, play/pause/stop, enable/disable)
// mutates the local RobotView in place, then pushState() sends the WHOLE
// object back (over the WebSocket if open, REST POST otherwise) - exactly
// like HYDRA-UMC-STUDIO's own browser UI (RobotDetail.tsx's updateRobot()).
// =============================================================================
package com.hydraumc.control.viewmodel

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hydraumc.control.model.BleDevice
import com.hydraumc.control.model.HydraState
import com.hydraumc.control.model.RobotView
import com.hydraumc.control.model.ServerInfo
import com.hydraumc.control.R
import com.hydraumc.control.network.ConnectionPrefs
import com.hydraumc.control.network.HydraApiClient
import com.hydraumc.control.network.HydraApiException
import com.hydraumc.control.network.HydraWebSocket
import com.hydraumc.control.network.HydraBleClient
import com.hydraumc.control.network.WsStatus
import com.hydraumc.control.network.scanSubnets
import kotlinx.coroutines.launch
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.annotation.SuppressLint

/** 
 * Data class for ATC tools with slot and name. 
 * @property slot The ATC slot number.
 * @property name The name of the tool.
 */
data class AtcTool(val slot: Int, val name: String)

/** 
 * Flat, display-friendly snapshot of one RobotView.
 * @property id Robot unique ID.
 * @property name Robot name.
 * @property online Connection status.
 * @property isPlaying Movement status.
 * @property isPaused Pause status.
 * @property hasXYTable Presence of XY table.
 * @property hasAtc Presence of ATC.
 * @property currentTool Attached tool.
 * @property atcTools List of available tools.
 * @property speed Current speed.
 * @property acceleration Current acceleration.
 * @property posX X coordinate.
 * @property posY Y coordinate.
 * @property posZ Z coordinate.
 * @property xyPosX XY Table X coordinate.
 * @property xyPosY XY Table Y coordinate.
 */
data class RobotState(
    val id: Int,
    val name: String,
    val online: Boolean,
    val isPlaying: Boolean,
    val isPaused: Boolean,
    val hasXYTable: Boolean,
    val hasAtc: Boolean,
    val currentTool: String,
    val atcTools: List<AtcTool>,
    val speed: Double,
    val acceleration: Double,
    val posX: Double,
    val posY: Double,
    val posZ: Double,
    val xyPosX: Double,
    val xyPosY: Double,
)

/** 
 * Extension function to convert a RobotView model into a displayable RobotState. 
 */
private fun RobotView.toDisplay(): RobotState = RobotState(
    id = id,
    name = name,
    online = online,
    isPlaying = isPlaying,
    isPaused = isPaused,
    hasXYTable = hasXYTable,
    hasAtc = hasAtc,
    currentTool = tool,
    atcTools = atcTools.map { AtcTool(it.slot, it.tool) },
    speed = speed,
    acceleration = acceleration,
    posX = posAxis("x"),
    posY = posAxis("y"),
    posZ = posAxis("z"),
    xyPosX = xyTablePos.optDouble("x", 0.0),
    xyPosY = xyTablePos.optDouble("y", 0.0),
)

/**
 * Shared ViewModel responsible for robot orchestration, networking, and UI state management.
 * @param application The Android application context.
 */
class RobotViewModel(application: Application) : AndroidViewModel(application) {
    /** Current list of robots available for display. */
    val robots = mutableStateOf<List<RobotState>>(emptyList())
    /** ID of the currently selected robot in the Control screen. */
    val selectedRobotId = mutableStateOf<Int?>(null)

    /** User-defined target IP address. */
    val ipAddress = mutableStateOf("192.168.1.100")
    /** User-defined target port. */
    val port = mutableStateOf("3000")
    /** Overall network connection status string. */
    val connectionStatus = mutableStateOf(application.getString(R.string.status_disconnected))

    /** Latest error message for user feedback. */
    val lastError = mutableStateOf<String?>(null)

    /** List of servers found during LAN discovery. */
    val discoveredServers = mutableStateOf<List<ServerInfo>>(emptyList())
    /** Boolean flag for network scanning activity. */
    val isScanning = mutableStateOf(value = false)

    /** List of BLE devices found during scanning. */
    val discoveredBtDevices = mutableStateOf<List<BleDevice>>(emptyList())
    /** Boolean flag for BLE scanning activity. */
    val isBtScanning = mutableStateOf(value = false)
    /** Status of the Bluetooth adapter on the device. */
    val isBtEnabled = mutableStateOf(value = false)
    /** Active Bluetooth LE scan callback. */
    private var bleScanCallback: ScanCallback? = null

    /** Internal core state container. */
    private var state = HydraState.empty()
    /** Active REST API client. */
    private var apiClient: HydraApiClient? = null
    /** Active WebSocket connection. */
    private var ws: HydraWebSocket? = null
    /** Active Bluetooth LE client. */
    private var bleClient: HydraBleClient? = null
    /** Persistent connection preferences manager. */
    private val prefs = ConnectionPrefs(application)

    init {
        /** Load saved connection settings on initialization. */
        viewModelScope.launch {
            prefs.load()?.let { (savedIp, savedPort) ->
                ipAddress.value = savedIp
                port.value = savedPort
            }
        }
        
        /** Rationale: Initial check for Bluetooth adapter status. */
        val bluetoothManager = getApplication<Application>().getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        isBtEnabled.value = bluetoothManager.adapter?.isEnabled ?: false
    }

    /** 
     * Connects to a HYDRA-UMC server using the configured IP and Port.
     */
    fun connect() {
        val host = ipAddress.value.trim()
        val portValue = port.value.trim()
        if (host.isEmpty() || portValue.isEmpty()) {
            lastError.value = getApplication<Application>().getString(R.string.error_enter_ip_port)
            return
        }
        val portInt = portValue.toIntOrNull()
        if (portInt == null) {
            lastError.value = getApplication<Application>().getString(R.string.error_invalid_port, portValue)
            return
        }

        connectionStatus.value = getApplication<Application>().getString(R.string.status_connecting)
        lastError.value = null

        ws?.disconnect()
        bleClient?.disconnect()
        bleClient = null
        
        val client = HydraApiClient(host, portInt)
        apiClient = client

        viewModelScope.launch {
            prefs.save(host, portValue)
        }

        viewModelScope.launch {
            try {
                val settings = client.getSettings()
                applyState(HydraState(settings))
            } catch (e: HydraApiException) {
                lastError.value = e.message
            }
        }

        ws = HydraWebSocket(
            host = host,
            port = portInt,
            onStatus = { status ->
                connectionStatus.value = when (status) {
                    WsStatus.CONNECTING -> getApplication<Application>().getString(R.string.status_connecting)
                    WsStatus.CONNECTED -> getApplication<Application>().getString(R.string.status_connected)
                    WsStatus.DISCONNECTED -> getApplication<Application>().getString(R.string.status_disconnected)
                }
            },
            onSettings = { payload -> applyState(HydraState(payload)) },
        ) { message -> lastError.value = message }.also { it.connect() }
    }

    /** 
     * Internal helper to update the UI models when a new core state arrives.
     * @param newState The fresh HydraState object.
     */
    private fun applyState(newState: HydraState) {
        state = newState
        robots.value = newState.allRobots.map { it.toDisplay() }
        if (selectedRobotId.value == null || ((robots.value.none { it.id == selectedRobotId.value }))) {
            selectedRobotId.value = robots.value.firstOrNull()?.id
        }
    }

    /** 
     * Pushes the current local state back to the server.
     * Priority: BLE -> WebSocket -> REST API.
     */
    private fun pushState() {
        robots.value = state.allRobots.map { it.toDisplay() } 
        val payload = state.toJson()
        
        val sentOverBle = bleClient?.send(payload.toString()) ?: false
        if (sentOverBle) return
        
        val sentOverWs = ws?.send(payload) ?: false
        if (sentOverWs) return
        
        val client = apiClient ?: return
        viewModelScope.launch {
            try {
                client.postSettings(payload)
                lastError.value = null
            } catch (e: HydraApiException) {
                lastError.value = e.message
            }
        }
    }

    /** 
     * Helper to mutate a property of the selected robot and trigger a sync.
     * @param mutate Lambda that performs the mutation on a RobotView.
     */
    private fun mutateSelected(mutate: (RobotView) -> Unit) {
        val robotId = selectedRobotId.value ?: return
        val robotView = state.robotById(robotId) ?: run {
            lastError.value = getApplication<Application>().getString(R.string.error_robot_not_found)
            return
        }
        mutate(robotView)
        pushState()
    }

    /** 
     * Sends a top-level command to the selected robot.
     * @param command Command string (enable, disable, play, pause, stop).
     */
    fun sendCommand(command: String) {
        when (command) {
            "enable" -> mutateSelected { it.setOnline(value = true) }
            "disable" -> mutateSelected { it.setOnline(value = false) }
            "play" -> mutateSelected { it.setPlaying(playing = true) }
            "pause" -> mutateSelected { it.togglePaused() }
            "stop" -> mutateSelected { it.stop() }
            else -> lastError.value = getApplication<Application>().getString(R.string.error_unknown_command, command)
        }
    }

    /** 
     * Moves the robot or XY table along a specific axis.
     * @param target Either "robot" or "xytable".
     * @param axis The axis name (x, y, z).
     * @param amount The distance to move.
     */
    fun jog(target: String, axis: String, amount: Double) {
        mutateSelected { robot ->
            if (target == "xytable") {
                robot.setXyTableAxis(axis, robot.xyTablePos.optDouble(axis, 0.0) + amount)
            } else {
                robot.setPosAxis(axis, robot.posAxis(axis) + amount)
            }
        }
    }

    /** 
     * Updates the movement speed and acceleration for the selected robot.
     * @param speed Speed percentage.
     * @param acceleration Acceleration percentage.
     */
    fun setSpeed(speed: Double, acceleration: Double) {
        mutateSelected { robot ->
            robot.setSpeed(speed)
            robot.setAcceleration(acceleration)
        }
    }

    /** 
     * Triggers a tool change operation via the ATC.
     * @param slot The target ATC slot.
     */
    fun changeTool(slot: Int) {
        mutateSelected { robot ->
            val tool = robot.atcTools.find { it.slot == slot }
            if (tool == null) {
                lastError.value = getApplication<Application>().getString(R.string.error_no_tool_in_slot, slot)
            } else {
                robot.setTool(tool.tool)
            }
        }
    }

    /** 
     * Starts a subnet scan to find active HYDRA-UMC servers.
     */
    fun scanNetwork() {
        if (isScanning.value) return
        isScanning.value = true
        discoveredServers.value = emptyList()
        viewModelScope.launch {
            try {
                scanSubnets(HydraApiClient.sharedHttpClient, portValue()).collect { server ->
                    discoveredServers.value += server
                }
            } finally {
                isScanning.value = false
            }
        }
    }

    /** 
     * Starts a Bluetooth LE scan to find nearby robots.
     */
    @SuppressLint("MissingPermission")
    fun scanBluetooth() {
        if (isBtScanning.value) return
        
        val bluetoothManager = getApplication<Application>().getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = bluetoothManager.adapter
        if (adapter == null || !adapter.isEnabled) {
            isBtEnabled.value = false
            return
        }
        isBtEnabled.value = true

        isBtScanning.value = true
        discoveredBtDevices.value = emptyList()
        
        val scanner = adapter.bluetoothLeScanner
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val device = BleDevice(result.device.name, result.device.address, result.rssi)
                if (discoveredBtDevices.value.none { it.address == device.address }) {
                    discoveredBtDevices.value += device
                }
            }
        }
        bleScanCallback = callback

        viewModelScope.launch {
            scanner.startScan(callback)
            kotlinx.coroutines.delay(kotlin.time.Duration.parse("5s"))
            stopBtScan()
        }
    }

    /** 
     * Stops an active Bluetooth LE scan.
     */
    @SuppressLint("MissingPermission")
    private fun stopBtScan() {
        if (!isBtScanning.value) return
        val bluetoothManager = getApplication<Application>().getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val scanner = bluetoothManager.adapter?.bluetoothLeScanner
        bleScanCallback?.let { 
            scanner?.stopScan(it)
        }
        bleScanCallback = null
        isBtScanning.value = false
    }

    /** 
     * Connects to a robot using Bluetooth LE GATT.
     * @param device The BleDevice to connect to.
     */
    fun connectBle(device: BleDevice) {
        ws?.disconnect()
        bleClient?.disconnect()
        apiClient = null
        
        lastError.value = null
        connectionStatus.value = getApplication<Application>().getString(R.string.status_connecting)
        
        bleClient = HydraBleClient(
            context = getApplication(),
            deviceAddress = device.address,
            onStatus = { status ->
                connectionStatus.value = when (status) {
                    WsStatus.CONNECTING -> getApplication<Application>().getString(R.string.status_connecting)
                    WsStatus.CONNECTED -> getApplication<Application>().getString(R.string.status_connected)
                    WsStatus.DISCONNECTED -> getApplication<Application>().getString(R.string.status_disconnected)
                }
            },
            onSettings = { payload -> 
                try {
                    applyState(HydraState(org.json.JSONObject(payload)))
                } catch (e: Exception) {
                    lastError.value = e.message
                }
            },
        ) { message -> lastError.value = message }.also { it.connect() }
    }

    /** 
     * Disconnects the active Bluetooth LE client.
     */
    fun disconnectBle() {
        bleClient?.disconnect()
        bleClient = null
    }

    /** 
     * Internal helper to parse the port string safely.
     * @return The port number.
     */
    private fun portValue(): Int = port.value.toIntOrNull() ?: 3000

    /** 
     * Sets the target server details from discovery results and connects.
     * @param server The selected ServerInfo.
     */
    fun connectToDiscovered(server: ServerInfo) {
        ipAddress.value = server.host
        port.value = server.port.toString()
        connect()
    }

    override fun onCleared() {
        super.onCleared()
        ws?.disconnect()
        bleClient?.disconnect()
        stopBtScan()
    }
}
