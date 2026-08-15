// =============================================================================
// HYDRA-UMC Android Control - model/HydraState.kt
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

/** The 6-axis joint names every robot model in this ecosystem uses - see
 * HYDRA-UMC-STUDIO's own src/store.tsx RobotState.joints shape. */
val JOINT_NAMES = listOf("j1", "j2", "j3", "j4", "j5", "j6")

/** Thin, mutation-friendly view over one entry of controllers[].robots[]
 * (src/store.tsx RobotState). Reads/writes go straight through to the
 * underlying JSONObject - there is no detached copy to fall out of sync
 * with what actually gets sent back to the server. */
class RobotView(val raw: JSONObject) {
    val id: Int get() = raw.optInt("id", 0)
    val name: String get() = raw.optString("name", "Robot ${id}")
    val online: Boolean get() = raw.optBoolean("online", false)
    val model: String get() = raw.optString("model", "Generic (6-DOF)")
    val role: String get() = raw.optString("role", "Idle")
    val tool: String get() = raw.optString("tool", "None")
    val hasXYTable: Boolean get() = raw.optBoolean("hasXYTable", false)

    fun setOnline(value: Boolean) {
        raw.put("online", value)
    }

    fun setTool(value: String) {
        raw.put("tool", value)
    }

    // pos: { x, y, z, a, b, c, ... } - cartesian position, RobotState.pos in store.tsx.
    val pos: JSONObject get() = raw.optJSONObject("pos") ?: JSONObject().also { raw.put("pos", it) }
    fun posAxis(axis: String): Double = pos.optDouble(axis, 0.0)
    fun setPosAxis(axis: String, value: Double) {
        pos.put(axis, value)
    }

    // xyTable.pos: { x, y } - independent XY table, only meaningful when hasXYTable.
    val xyTablePos: JSONObject
        get() {
            val xyTable = raw.optJSONObject("xyTable") ?: JSONObject().also { raw.put("xyTable", it) }
            return xyTable.optJSONObject("pos") ?: JSONObject().also { xyTable.put("pos", it) }
        }
    fun setXyTableAxis(axis: String, value: Double) {
        xyTablePos.put(axis, value)
    }

    // playbackState: { isPlaying, isPaused, isFinished, isLooping, activeStep, speed, acceleration }
    val playbackState: JSONObject
        get() = raw.optJSONObject("playbackState") ?: JSONObject().also { raw.put("playbackState", it) }
    val isPlaying: Boolean get() = playbackState.optBoolean("isPlaying", false)
    val isPaused: Boolean get() = playbackState.optBoolean("isPaused", false)
    val speed: Double get() = playbackState.optDouble("speed", 100.0)
    val acceleration: Double get() = playbackState.optDouble("acceleration", 100.0)

    /** Mirrors HYDRA-UMC-STUDIO's own RobotDetail.tsx play button: sets isPlaying,
     * resets activeStep to 0, clears isFinished - keeps speed/acceleration as-is. */
    fun setPlaying(playing: Boolean) {
        val pb = playbackState
        pb.put("isPlaying", playing)
        if (playing) {
            pb.put("activeStep", 0)
            pb.put("isFinished", false)
            pb.put("isPaused", false)
        } else {
            pb.put("activeStep", -1)
        }
    }

    fun togglePaused() {
        playbackState.put("isPaused", !isPaused)
    }

    fun stop() {
        val pb = playbackState
        pb.put("isPlaying", false)
        pb.put("isPaused", false)
        pb.put("activeStep", -1)
    }

    fun setSpeed(value: Double) {
        playbackState.put("speed", value)
    }

    fun setAcceleration(value: Double) {
        playbackState.put("acceleration", value)
    }

    // atc.tools[]: { slot, tool } - see store.tsx ATCConfig.tools.
    data class AtcTool(val slot: Int, val tool: String)

    val atcTools: List<AtcTool>
        get() {
            val atc = raw.optJSONObject("atc") ?: return emptyList()
            val tools = atc.optJSONArray("tools") ?: return emptyList()
            return (0 until tools.length()).mapNotNull { i ->
                val t = tools.optJSONObject(i) ?: return@mapNotNull null
                AtcTool(t.optInt("slot", 0), t.optString("tool", "None"))
            }
        }
    val hasAtc: Boolean get() = atcTools.isNotEmpty()

    override fun toString() = "RobotView(id=$id, name=$name, online=$online)"
}

/** Thin view over one entry of the top-level controllers[] array
 * (src/store.tsx HydraController). */
class ControllerView(val raw: JSONObject) {
    val id: String get() = raw.optString("id", "")
    val name: String get() = raw.optString("name", id)
    val ip: String get() = raw.optString("ip", "")

    val robots: List<RobotView>
        get() {
            val arr = raw.optJSONArray("robots") ?: return emptyList()
            return (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.let(::RobotView) }
        }

    fun robotById(robotId: Int): RobotView? = robots.find { it.id == robotId }
}

/** Wraps one server's full {settings, controllers, activeControllerId} payload -
 * exactly the shape GET /api/settings returns and POST /api/settings expects
 * back, per HYDRA-UMC-STUDIO/docs/REMOTE_API.md section 2, and the same
 * envelope a WebSocket "settings" message carries as its payload (section 3). */
class HydraState(val raw: JSONObject) {
    val controllers: List<ControllerView>
        get() {
            val arr = raw.optJSONArray("controllers") ?: return emptyList()
            return (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.let(::ControllerView) }
        }

    val activeControllerId: String get() = raw.optString("activeControllerId", "")

    val allRobots: List<RobotView> get() = controllers.flatMap { it.robots }

    fun robotById(robotId: Int): RobotView? = allRobots.find { it.id == robotId }

    /** The exact JSON to POST back / send over the settings WebSocket message -
     * just the raw payload, since every accessor above mutates it in place
     * rather than a detached copy (same approach as HYDRA-UMC-SUITE's own
     * HydraState.to_json_dict()). */
    fun toJson(): JSONObject = raw

    companion object {
        fun empty(): HydraState = HydraState(
            JSONObject()
                .put("settings", JSONObject())
                .put("controllers", JSONArray())
                .put("activeControllerId", "")
        )
    }
}

/** One entry in the discovery/connection list - see
 * HYDRA-UMC-STUDIO/docs/REMOTE_API.md section 1 (GET /api/hydra-info)
 * for the wire shape this is built from. Mirrors HYDRA-UMC-SUITE's own
 * hydra_suite/models.py ServerInfo. */
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
    val baseUrl: String get() = "http://$host:$port"
    val wsUrl: String get() = "ws://$host:$port/ws"
    val displayName: String get() = hostname.ifBlank { host }

    companion object {
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
