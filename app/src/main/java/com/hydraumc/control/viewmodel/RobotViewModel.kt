package com.hydraumc.control.viewmodel

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hydraumc.control.network.*
import kotlinx.coroutines.launch
import org.json.JSONObject

data class AtcTool(val slot: Int, val name: String)

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

class RobotViewModel : ViewModel() {
    val robots = mutableStateOf<List<RobotState>>(emptyList())
    val selectedRobotId = mutableStateOf<Int?>(null)
    
    val ipAddress = mutableStateOf("192.168.1.100")
    val port = mutableStateOf("3000")
    val connectionStatus = mutableStateOf("Desconectado")
    
    private var webSocketManager: WebSocketManager? = null
    private var apiService: ApiService? = null
    
    fun connect() {
        connectionStatus.value = "Conectando..."
        apiService = ApiService.create("${ipAddress.value}:${port.value}")
        
        webSocketManager?.disconnect()
        webSocketManager = WebSocketManager(ipAddress.value, port.value)
        webSocketManager?.connect { message ->
            try {
                val json = JSONObject(message)
                if (json.optString("type") == "settings") {
                    val payload = json.getJSONObject("payload")
                    val controllers = payload.optJSONArray("controllers")
                    if (controllers != null && controllers.length() > 0) {
                        val parsedRobots = mutableListOf<RobotState>()
                        for (i in 0 until controllers.length()) {
                            val controller = controllers.getJSONObject(i)
                            val robs = controller.optJSONArray("robots") ?: continue
                            for (j in 0 until robs.length()) {
                                val r = robs.getJSONObject(j)
                                val pb = r.optJSONObject("playbackState")
                                
                                // Parse ATC tools
                                val atcTools = mutableListOf<AtcTool>()
                                val atc = r.optJSONObject("atc")
                                var hasAtc = false
                                if (atc != null) {
                                    val toolsArray = atc.optJSONArray("tools")
                                    if (toolsArray != null && toolsArray.length() > 0) {
                                        hasAtc = true
                                        for (k in 0 until toolsArray.length()) {
                                            val t = toolsArray.getJSONObject(k)
                                            atcTools.add(AtcTool(t.getInt("slot"), t.getString("tool")))
                                        }
                                    }
                                }
                                
                                val pos = r.optJSONObject("pos")
                                val xyTable = r.optJSONObject("xyTable")
                                val xyPos = xyTable?.optJSONObject("pos")
                                
                                parsedRobots.add(
                                    RobotState(
                                        id = r.getInt("id"),
                                        name = r.getString("name"),
                                        online = r.optBoolean("online", false),
                                        isPlaying = pb?.optBoolean("isPlaying", false) ?: false,
                                        isPaused = pb?.optBoolean("isPaused", false) ?: false,
                                        hasXYTable = r.optBoolean("hasXYTable", false),
                                        hasAtc = hasAtc,
                                        currentTool = r.optString("tool", "None"),
                                        atcTools = atcTools,
                                        speed = pb?.optDouble("speed", 100.0) ?: 100.0,
                                        acceleration = pb?.optDouble("acceleration", 500.0) ?: 500.0,
                                        posX = pos?.optDouble("x", 0.0) ?: 0.0,
                                        posY = pos?.optDouble("y", 0.0) ?: 0.0,
                                        posZ = pos?.optDouble("z", 0.0) ?: 0.0,
                                        xyPosX = xyPos?.optDouble("x", 0.0) ?: 0.0,
                                        xyPosY = xyPos?.optDouble("y", 0.0) ?: 0.0
                                    )
                                )
                            }
                        }
                        robots.value = parsedRobots
                        connectionStatus.value = "Conectado"
                        
                        if (selectedRobotId.value == null && parsedRobots.isNotEmpty()) {
                            selectedRobotId.value = parsedRobots.first().id
                        }
                    }
                }
            } catch (e: Exception) {
                if (message.contains("error")) {
                    connectionStatus.value = "Error: $message"
                }
            }
        }
    }
    
    fun sendCommand(command: String) {
        val robotId = selectedRobotId.value ?: return
        viewModelScope.launch {
            try {
                apiService?.sendCommand(robotId, CommandRequest(command))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun jog(target: String, axis: String, amount: Double) {
        val robotId = selectedRobotId.value ?: return
        viewModelScope.launch {
            try {
                apiService?.jogRobot(robotId, JogRequest(target, axis, amount))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    fun setSpeed(speed: Double, acceleration: Double) {
        val robotId = selectedRobotId.value ?: return
        viewModelScope.launch {
            try {
                apiService?.setSpeed(robotId, SpeedRequest(speed, acceleration))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    fun changeTool(slot: Int) {
        val robotId = selectedRobotId.value ?: return
        viewModelScope.launch {
            try {
                apiService?.changeTool(robotId, AtcRequest(slot))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        webSocketManager?.disconnect()
    }
}
