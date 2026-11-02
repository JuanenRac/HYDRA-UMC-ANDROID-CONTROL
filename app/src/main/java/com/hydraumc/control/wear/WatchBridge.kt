// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - What the watch sends the phone to pass on: presence, confirmation, plant summary
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.wear

import com.hydraumc.control.model.HydraState
import com.hydraumc.control.network.RobotCommandContract
import org.json.JSONObject

/** Paths of the messages the watch sends the phone besides the voice turn (they match the watch's own transport). */
object WatchBridgePaths {
    const val ALERT = "/hydra-umc/alert/v1"
    const val PRESENCE = "/hydra-umc/presence/v1"
    const val VOICE_CONFIRM = "/hydra-umc/voice-confirm/v1"
}

/**
 * The presence of the operator, as the watch sends it every few seconds while the operator has switched it on. The phone
 * only passes it on; the server decides what a lost heartbeat means.
 */
data class WatchPresenceHeartbeat(val sequence: Long, val intervalSeconds: Int, val left: Boolean, val protocolVersion: Int = PROTOCOL_VERSION) {
    init {
        require(protocolVersion == PROTOCOL_VERSION) { "unsupported presence protocol version" }
        require(sequence >= 0 && intervalSeconds in 1..MAX_INTERVAL_SECONDS) { "presence needs a sequence from 0 and an interval from 1 to $MAX_INTERVAL_SECONDS seconds" }
    }

    /** The body for the server: the heartbeat plus how many the phone did not receive since the last one. */
    fun toServerJson(missed: Long): JSONObject = JSONObject()
        .put("type", "presence_heartbeat")
        .put("protocolVersion", protocolVersion)
        .put("sequence", sequence)
        .put("intervalSeconds", intervalSeconds)
        .put("state", if (left) "LEFT" else "PRESENT")
        .put("missed", missed)

    companion object {
        const val PROTOCOL_VERSION = 1
        const val MAX_INTERVAL_SECONDS = 60

        fun fromJson(json: JSONObject): WatchPresenceHeartbeat {
            require(json.getString("type") == "presence_heartbeat") { "unexpected presence message type" }
            val state = json.optString("state", "PRESENT")
            require(state == "PRESENT" || state == "LEFT") { "presence state must be PRESENT or LEFT" }
            return WatchPresenceHeartbeat(
                sequence = json.getLong("sequence"),
                intervalSeconds = json.getInt("intervalSeconds"),
                left = state == "LEFT",
                protocolVersion = json.optInt("protocolVersion", PROTOCOL_VERSION),
            )
        }
    }
}

/** Counts the heartbeats of the watch that never reached the phone, from the gaps in their sequence numbers. */
class WatchPresenceTracker {
    private var last: Long? = null

    /** How many heartbeats were lost before this one (0 for the first, after a restart of the watch's counter, or for a repeat). */
    @Synchronized
    fun onHeartbeat(heartbeat: WatchPresenceHeartbeat): Long {
        val before = last
        last = heartbeat.sequence
        return if (before == null || heartbeat.sequence <= before) 0L else heartbeat.sequence - before - 1
    }

    @Synchronized
    fun reset() {
        last = null
    }
}

/**
 * The second tap of the two-step confirmation of a spoken order. The watch cannot move a robot: the phone sends this to the
 * server only when it has a session, and the server requires the reservation of the robot as for any command.
 */
data class WatchVoiceConfirm(val requestId: String, val confirms: String, val confirmationToken: String) {
    init {
        require(REQUEST_ID.matches(requestId) && REQUEST_ID.matches(confirms)) { "invalid voice confirmation request id" }
        require(TOKEN.matches(confirmationToken)) { "invalid confirmation token" }
    }

    fun toServerJson(): JSONObject = JSONObject()
        .put("type", "voice_confirm")
        .put("requestId", requestId)
        .put("confirms", confirms)
        .put("confirmationToken", confirmationToken)

    /** Never print the token: it is a one-use secret. */
    override fun toString(): String = "WatchVoiceConfirm(requestId=$requestId, confirms=$confirms)"

    companion object {
        private val REQUEST_ID = Regex("^[A-Za-z0-9_-]{1,64}$")
        private val TOKEN = Regex("^[A-Za-z0-9._~+/=-]{16,${RobotCommandContract.VOICE_CONFIRM_TOKEN_MAX}}$")

        fun fromJson(json: JSONObject): WatchVoiceConfirm {
            require(json.getString("type") == "voice_confirm") { "unexpected voice confirmation type" }
            return WatchVoiceConfirm(json.getString("requestId"), json.getString("confirms"), json.getString("confirmationToken"))
        }

        /** True when [token] has the form the watch accepts. */
        fun isValidToken(token: String): Boolean = TOKEN.matches(token)
    }
}

/** The two optional numbers the complication of the watch shows, from the state the phone holds. */
object WatchPlantSummary {
    /** Robots that are playing a trajectory and not paused. */
    fun robotsRunning(state: HydraState): Int = state.allRobots.count { it.isPlaying && !it.isPaused }.coerceIn(0, 10000)

    /**
     * An alarm is active when a robot that is online has been put in safe mode (the server refuses everything but a stop for it).
     * Safety zones and endstops add to this once the server reports them.
     */
    fun alarmActive(state: HydraState): Boolean = state.allRobots.any { it.online && !it.isEnabled }
}
