// =============================================================================
// HYDRA-UMC CONTROL - Core state model wrapping the remote API JSON structure
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0 - see LICENSE
//
// Deliberately NOT a strict data-class schema that (de)serializes the full
// settings.json shape field-by-field. HYDRA-UMC STUDIO's own real state
// (src/store.tsx's SystemSettings/HydraController/RobotState) has many
// fields this app never needs to display or edit (UI layout prefs,
// per-integration IP/port blocks, kinematic-brain stage, etc.) - a strict
// schema would either have to model every one of them (large, constantly
// drifting out of sync with the real TypeScript source of truth) or
// silently DROP any field it doesn't know about on the next write-back,
// corrupting the real app's state for anyone still using the browser UI
// or HYDRA-UMC SUITE. Instead, HydraState wraps the raw JSONObject tree
// exactly as received from GET/POST /api/settings (see
// HYDRA-UMC-STUDIO/docs/REMOTE_API.md section 2) and only exposes
// convenience accessors for the handful of fields this app actually
// reads/writes - every other field round-trips untouched.
//
// This mirrors HYDRA-UMC-SUITE's own hydra_suite/models.py (RobotView /
// ControllerView / HydraState) field-for-field and reasoning-for-reasoning -
// see that file's own header comment for the original rationale.
// =============================================================================
package com.hydraumc.control.model

import org.json.JSONArray
import org.json.JSONObject

/** 
 * The 6-axis joint names every robot model in this ecosystem uses. 
 */
val JOINT_NAMES = listOf("j1", "j2", "j3", "j4", "j5", "j6")

/** 
 * Thin, mutation-friendly view over one entry of controllers[].robots[].
 * @property raw The underlying JSONObject holding the robot data.
 */
class RobotView(val raw: JSONObject) {
    /** Unique identifier for the robot. */
    val id: Int get() = raw.optInt("id", 0)
    /** Human-readable name of the robot. */
    val name: String get() = raw.optString("name", "Robot ${id}")
    /** Connection status of the robot. */
    val online: Boolean get() = raw.optBoolean("online", false)
    /** The mechanical model type of the robot. */
    val model: String get() = raw.optString("model", "Generic (6-DOF)")
    /** Current operational role assigned to the robot. */
    val role: String get() = raw.optString("role", "Idle")
    /** Currently attached tool. */
    val tool: String get() = raw.optString("tool", "None")
    /** Whether the robot is mounted on an independent XY table. */
    val hasXYTable: Boolean get() = raw.optBoolean("hasXYTable", false)

    /** Returns the manufacturer based on the robot model. */
    val manufacturer: String get() {
        val m = model
        return when {
            m.contains("Parol6") || m.contains("Faze4") -> "Source Robotics"
            m.contains("AR3") || m.contains("AR4") -> "Annin Robotics"
            m.contains("UR") || m.contains("Universal") -> "Universal Robots"
            m.contains("xArm") || m.contains("Lite 6") -> "UFACTORY"
            m.contains("Gen3") || m.contains("Gen2") -> "Kinova"
            m.contains("ViperX") || m.contains("WidowX") -> "Trossen Robotics"
            m.contains("e.DO") -> "Comau"
            m.contains("Z1") -> "Unitree"
            m.contains("PiPER") -> "AgileX"
            else -> "Generic"
        }
    }

    /** Module flags */
    private fun isModuleEnabled(key: String): Boolean = raw.optJSONObject(key)?.optBoolean("enabled", false) ?: false

    val hasPnP: Boolean get() = isModuleEnabled("juanenPnP") || isModuleEnabled("lumenPnP")
    val hasCNC: Boolean get() = isModuleEnabled("juanenCNC")
    val hasLaser: Boolean get() = isModuleEnabled("juanenLaser")
    val hasHeatedBed: Boolean get() = isModuleEnabled("heatedBed")
    val hasVacuumTable: Boolean get() = isModuleEnabled("vacuumTable")
    val hasCamera: Boolean get() = raw.optBoolean("visionEnabled", false) || raw.has("cameraView") || raw.has("camera")
    val hasAtc: Boolean get() = raw.has("atc")
    val hasRack: Boolean get() = isModuleEnabled("rackSystem")

    /** Updates the online status in the raw JSON. */
    fun setOnline(value: Boolean) {
        raw.put("online", value)
    }

    /** Updates the attached tool in the raw JSON. */
    fun setTool(value: String) {
        raw.put("tool", value)
    }

    /** Gets the list of valves as a JSONArray. */
    val valves: JSONArray get() = raw.optJSONArray("valves") ?: JSONArray().also { raw.put("valves", it) }
    
    /** Sets the state of a specific valve. */
    fun setValve(index: Int, state: Boolean) {
        valves.put(index, state)
    }

    /** Gets the list of pumps as a JSONArray. */
    val pumps: JSONArray get() = raw.optJSONArray("pumps") ?: JSONArray().also { raw.put("pumps", it) }
    
    /** Sets the state of a specific pump. */
    fun setPump(index: Int, state: Boolean) {
        pumps.put(index, state)
    }

    /** Robot-level joints {j1, j2, j3, j4, j5, j6}. */
    val joints: JSONObject get() = raw.optJSONObject("joints") ?: JSONObject().also { raw.put("joints", it) }
    
    /** Sets a specific joint angle. */
    fun setJoint(joint: String, value: Double) {
        joints.put(joint, value)
    }

    /** The Cartesian position object {x, y, z, a, b, c}. */
    val pos: JSONObject get() = raw.optJSONObject("pos") ?: JSONObject().also { raw.put("pos", it) }
    
    /** Gets the value of a specific position axis. */
    fun posAxis(axis: String): Double = pos.optDouble(axis, 0.0)
    
    /** Sets the value of a specific position axis. */
    fun setPosAxis(axis: String, value: Double) {
        pos.put(axis, value)
    }

    /** The Cartesian position of the XY table, if present. */
    val xyTablePos: JSONObject
        get() {
            val xyTable = raw.optJSONObject("xyTable") ?: JSONObject().also { raw.put("xyTable", it) }
            return xyTable.optJSONObject("pos") ?: JSONObject().also { xyTable.put("pos", it) }
        }
    
    /** Sets the value of a specific axis for the XY table. */
    fun setXyTableAxis(axis: String, value: Double) {
        // Update Robot-level xyTable (for UI/Browser sync)
        xyTablePos.put(axis, value)
        
        // Coordinated update: Mirror to pos.tx/ty for kinematic consistency
        if (axis == "x") pos.put("tx", value)
        if (axis == "y") pos.put("ty", value)
    }

    /** The playback state object for robot movements. */
    val playbackState: JSONObject
        get() = raw.optJSONObject("playbackState") ?: JSONObject().also { raw.put("playbackState", it) }
    
    /** Whether a movement sequence is currently playing. */
    val isPlaying: Boolean get() = playbackState.optBoolean("isPlaying", false) || playbackState.optBoolean("playing", false)
    /** Whether the current movement sequence is paused. */
    val isPaused: Boolean get() = playbackState.optBoolean("isPaused", false) || playbackState.optBoolean("paused", false)
    /** Playback speed percentage. */
    val speed: Double get() = playbackState.optDouble("speed", 100.0)
    /** Playback acceleration percentage. */
    val acceleration: Double get() = playbackState.optDouble("acceleration", 100.0)

    /** 
     * Controls the playback state, resetting markers when starting. 
     */
    fun setPlaying(playing: Boolean) {
        val pb = playbackState
        pb.put("isPlaying", playing)
        pb.put("playing", playing)
        pb.put("requestStop", !playing) // Explicit stop request for browser sync
        if (playing) {
            pb.put("activeStep", 0)
            pb.put("isFinished", false)
            pb.put("finished", false)
            pb.put("isPaused", false)
            pb.put("paused", false)
            pb.put("requestPause", false)
        } else {
            pb.put("activeStep", -1)
            pb.put("isFinished", false)
            pb.put("finished", false)
        }
    }

    /** Toggles the pause state of playback. */
    fun togglePaused() {
        val newVal = !isPaused
        val pb = playbackState
        pb.put("isPaused", newVal)
        pb.put("paused", newVal)
        pb.put("requestPause", newVal) // Explicit pause request for browser sync
        pb.put("isPlaying", true)
        pb.put("playing", true)
        pb.put("isFinished", false)
        pb.put("finished", false)
    }

    /** Stops playback and resets state. */
    fun stop() {
        val pb = playbackState
        pb.put("isPlaying", false)
        pb.put("playing", false)
        pb.put("requestStop", true) // Trigger browser-side globalPlaybacks[id] = false
        pb.put("isPaused", false)
        pb.put("paused", false)
        pb.put("requestPause", false)
        pb.put("activeStep", -1)
    }

    /** Sets the playback speed percentage. */
    fun setSpeed(value: Double) {
        playbackState.put("speed", value)
    }

    /** Sets the playback acceleration percentage. */
    fun setAcceleration(value: Double) {
        playbackState.put("acceleration", value)
    }

    /** 
     * Represents a tool configured in the Automatic Tool Changer. 
     * @property slot The ATC slot number.
     * @property tool The name of the tool in that slot.
     */
    data class AtcTool(val slot: Int, val tool: String)

    /** List of tools available in the Automatic Tool Changer. */
    val atcTools: List<AtcTool>
        get() {
            val atc = raw.optJSONObject("atc") ?: return emptyList()
            val tools = atc.optJSONArray("tools") ?: return emptyList()
            return (0 until tools.length()).mapNotNull { i ->
                val t = tools.optJSONObject(i) ?: return@mapNotNull null
                AtcTool(t.optInt("slot", 0), t.optString("tool", "None"))
            }
        }
    
    /** Whether the robot has an Automatic Tool Changer configured. */
    // Logic moved to hasAtc flag for better UI consistency

    override fun toString() = "RobotView(id=$id, name=$name, online=$online)"
}

/** 
 * Thin view over one entry of the top-level controllers[] array.
 * @property raw The underlying JSONObject holding the controller data.
 */
class ControllerView(val raw: JSONObject) {
    /** Unique ID of the controller. */
    val id: String get() = raw.optString("id", "")
    /** Human-readable name of the controller. */
    val name: String get() = raw.optString("name", id)
    /** Network IP address of the controller. */
    val ip: String get() = raw.optString("ip", "")

    /** Kinematic Brain Stage (Controller-level XY table) */
    val kinematicBrainStage: JSONObject 
        get() = raw.optJSONObject("kinematicBrainStage") ?: JSONObject().also { raw.put("kinematicBrainStage", it) }
    
    val kbXyTable: JSONObject
        get() = kinematicBrainStage.optJSONObject("xyTable") ?: JSONObject().also { kinematicBrainStage.put("xyTable", it) }

    /** Sets controller-level XY table axis. */
    fun setKbXyTableAxis(axis: String, value: Double) {
        // KB Stage uses x, y1, y2, z directly
        if (axis == "x") kbXyTable.put("x", value)
        if (axis == "y") {
            kbXyTable.put("y1", value)
            kbXyTable.put("y2", value)
        }
        if (axis == "z") kbXyTable.put("z", value)
    }

    /** List of robots managed by this controller. */
    val robots: List<RobotView>
        get() {
            val arr = raw.optJSONArray("robots") ?: return emptyList()
            return (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.let(::RobotView) }
        }

    /** Finds a robot by its ID within this controller. */
    fun robotById(robotId: Int): RobotView? = robots.find { it.id == robotId }
}

/** 
 * Thin view over a job/trajectory file.
 * @property raw Underlying JSONObject for the job.
 */
class JobView(val raw: JSONObject) {
    /** Unique name of the job file. */
    val name: String get() = raw.optString("name", "Unknown Job")
    /** File size in bytes. */
    val size: Long get() = raw.optLong("size", 0)
    /** Last modification timestamp. */
    val lastModified: String get() = raw.optString("lastModified", "")
}

/** 
 * Wraps the full system state payload received from the server.
 * @property raw The full JSONObject representing the entire system settings.
 */
class HydraState(val raw: JSONObject) {
    /** List of all controllers in the system. */
    val controllers: List<ControllerView>
        get() {
            val arr = raw.optJSONArray("controllers") ?: return emptyList()
            return (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.let(::ControllerView) }
        }

    /** The ID of the currently active controller. */
    val activeControllerId: String get() = raw.optString("activeControllerId", "")

    /** Flattened list of all robots across all controllers. */
    val allRobots: List<RobotView> get() = controllers.flatMap { it.robots }

    /** List of all jobs/trajectories available on the server. */
    val allJobs: List<JobView>
        get() {
            val arr = raw.optJSONArray("jobs") ?: return emptyList()
            return (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.let(::JobView) }
        }

    /** Finds a robot by its ID across the entire system. */
    fun robotById(robotId: Int): RobotView? = allRobots.find { it.id == robotId }

    /** 
     * Returns the raw JSONObject for serialization back to the server. 
     */
    fun toJson(): JSONObject = raw

    companion object {
        /** Creates an empty system state. */
        fun empty(): HydraState = HydraState(
            JSONObject()
                .put("settings", JSONObject())
                .put("controllers", JSONArray())
                .put("activeControllerId", "")
        )
    }
}

/** 
 * Metadata for a discovered Hydra server.
 * @property host The network host/IP.
 * @property port The network port (default 3000).
 * @property product Product identifier.
 * @property remoteApiVersion Supported API version.
 * @property appVersion Software version string.
 * @property hostname System hostname.
 * @property controllerCount Number of active controllers.
 * @property robotCount Total number of robots.
 * @property uptimeSeconds System uptime in seconds.
 */
data class ServerInfo(
    val host: String,
    val port: Int = 3000,
    val product: String = "",
    val remoteApiVersion: Int = 0,
    val appVersion: String = "",
    val hostname: String = "",
    val controllerCount: Int = 0,
    val robotCount: Int = 0,
    val uptimeSeconds: Int = 0,
) {
    /** Base HTTP URL for API calls. */
    val baseUrl: String get() = "http://$host:$port"
    /** WebSocket URL for real-time updates. */
    val wsUrl: String get() = "ws://$host:$port/ws"
    /** Display name for the server list. */
    val displayName: String get() = hostname.ifBlank { host }

    companion object {
        /** 
         * Factory method to create ServerInfo from a discovery payload. 
         */
        fun fromHydraInfo(host: String, port: Int, payload: JSONObject): ServerInfo = ServerInfo(
            host = host,
            port = port,
            product = payload.optString("product", ""),
            remoteApiVersion = payload.optInt("remoteApiVersion", 0),
            appVersion = payload.optString("appVersion", ""),
            hostname = payload.optString("hostname", ""),
            controllerCount = payload.optInt("controllerCount", 0),
            robotCount = payload.optInt("robotCount", 0),
            uptimeSeconds = payload.optInt("uptimeSeconds", 0),
        )
    }
}
