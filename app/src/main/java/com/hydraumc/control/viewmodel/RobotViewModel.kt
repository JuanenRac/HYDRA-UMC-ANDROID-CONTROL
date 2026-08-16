// =============================================================================
// HYDRA-UMC Android Control - viewmodel/RobotViewModel.kt
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
//
// Previously this class called 4 REST endpoints
// (POST /api/robots/{id}/command|jog|speed|atc) that do not exist on any
// real server, and every failure was swallowed with e.printStackTrace() -
// both fixed here.
// =============================================================================
package com.hydraumc.control.viewmodel

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hydraumc.control.model.HydraState
import com.hydraumc.control.model.RobotView
import com.hydraumc.control.model.ServerInfo
import com.hydraumc.control.R
import com.hydraumc.control.network.ConnectionPrefs
import com.hydraumc.control.network.HydraApiClient
import com.hydraumc.control.network.HydraApiException
import com.hydraumc.control.network.HydraWebSocket
import com.hydraumc.control.network.WsStatus
import com.hydraumc.control.network.scanSubnets
import kotlinx.coroutines.launch

data class AtcTool(val slot: Int, val name: String)

/** Flat, display-friendly snapshot of one RobotView - Compose reads this,
 * mutations go through RobotViewModel's action methods instead (which
 * mutate the underlying HydraState and push it back to the server). */
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
    val xyPosY: Double
)

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

class RobotViewModel(application: Application) : AndroidViewModel(application) {
    val robots = mutableStateOf<List<RobotState>>(emptyList())
    val selectedRobotId = mutableStateOf<Int?>(null)

    val ipAddress = mutableStateOf("192.168.1.100")
    val port = mutableStateOf("3000")
    val connectionStatus = mutableStateOf(application.getString(R.string.status_disconnected))

    /** Real, user-visible error state - every failed API/WS call lands here
     * instead of a silent printStackTrace(). Screens should show this near
     * connectionStatus and let the user dismiss/retry. */
    val lastError = mutableStateOf<String?>(null)

    val discoveredServers = mutableStateOf<List<ServerInfo>>(emptyList())
    val isScanning = mutableStateOf(false)

    private var state = HydraState.empty()
    private var apiClient: HydraApiClient? = null
    private var ws: HydraWebSocket? = null
    private val prefs = ConnectionPrefs(application)

    init {
        viewModelScope.launch {
            prefs.load()?.let { (savedIp, savedPort) ->
                ipAddress.value = savedIp
                port.value = savedPort
            }
        }
    }

    /** GET /api/settings once + open /ws - mirrors HydraConnection.connect()
     * in HYDRA-UMC SUITE's own client.py: the initial REST fetch and the
     * WebSocket are independent, so a server that's up but whose REST fetch
     * raced a restart isn't given up on immediately. */
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
                // Still try the WebSocket below - see this function's own header comment.
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
            onError = { message -> lastError.value = message },
        ).also { it.connect() }
    }

    private fun applyState(newState: HydraState) {
        state = newState
        robots.value = newState.allRobots.map { it.toDisplay() }
        if (selectedRobotId.value == null || robots.value.none { it.id == selectedRobotId.value }) {
            selectedRobotId.value = robots.value.firstOrNull()?.id
        }
    }

    /** Sends the WHOLE current state back - over the WebSocket if it's open
     * (avoids a second HTTP round trip), REST POST otherwise. Mirrors
     * HydraConnection.push_state() in client.py. Every action method below
     * goes through this. */
    private fun pushState() {
        robots.value = state.allRobots.map { it.toDisplay() } // keep the UI list in sync with the local mutation immediately
        val payload = state.toJson()
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

    private fun mutateSelected(mutate: (RobotView) -> Unit) {
        val robotId = selectedRobotId.value ?: return
        val robotView = state.robotById(robotId) ?: run {
            lastError.value = getApplication<Application>().getString(R.string.error_robot_not_found)
            return
        }
        mutate(robotView)
        pushState()
    }

    fun sendCommand(command: String) {
        when (command) {
            "enable" -> mutateSelected { it.setOnline(true) }
            "disable" -> mutateSelected { it.setOnline(false) }
            "play" -> mutateSelected { it.setPlaying(true) }
            "pause" -> mutateSelected { it.togglePaused() }
            "stop" -> mutateSelected { it.stop() }
            else -> lastError.value = getApplication<Application>().getString(R.string.error_unknown_command, command)
        }
    }

    fun jog(target: String, axis: String, amount: Double) {
        mutateSelected { robot ->
            if (target == "xytable") {
                robot.setXyTableAxis(axis, robot.xyTablePos.optDouble(axis, 0.0) + amount)
            } else {
                robot.setPosAxis(axis, robot.posAxis(axis) + amount)
            }
        }
    }

    fun setSpeed(speed: Double, acceleration: Double) {
        mutateSelected { robot ->
            robot.setSpeed(speed)
            robot.setAcceleration(acceleration)
        }
    }

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

    /** GET /api/hydra-info subnet scan (REMOTE_API.md section 1) - mirrors
     * HYDRA-UMC SUITE's own hydra_suite/net/discovery.py scan_subnets(). */
    fun scanNetwork() {
        if (isScanning.value) return
        isScanning.value = true
        discoveredServers.value = emptyList()
        viewModelScope.launch {
            try {
                scanSubnets(HydraApiClient.sharedHttpClient, portValue()).collect { server ->
                    discoveredServers.value = discoveredServers.value + server
                }
            } finally {
                isScanning.value = false
            }
        }
    }

    private fun portValue(): Int = port.value.toIntOrNull() ?: 3000

    fun connectToDiscovered(server: ServerInfo) {
        ipAddress.value = server.host
        port.value = server.port.toString()
        connect()
    }

    override fun onCleared() {
        super.onCleared()
        ws?.disconnect()
    }
}
