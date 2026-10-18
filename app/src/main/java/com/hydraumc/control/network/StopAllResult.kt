// =============================================================================
// HYDRA-UMC CONTROL - Result of the global STOP (POST /api/stop-all)
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0 - see LICENSE
// =============================================================================
package com.hydraumc.control.network

import com.hydraumc.control.util.optStr
import org.json.JSONObject

/** What the server did to one robot when the global STOP reached it. */
data class StopAllRobot(
    val robotId: Int,
    val controllerId: String,
    val name: String,
    val stopped: Boolean,
    val valvesOff: Boolean,
    val pumpsOff: Boolean,
    /** The account that holds the reservation, when it is someone else (the stop ignores it, but the operator should know). */
    val reservedBy: String?,
    val error: String?,
)

/** The server's answer to the global STOP: one entry per robot plus the totals. */
data class StopAllResult(
    val success: Boolean,
    val stoppedCount: Int,
    val failedCount: Int,
    /** False when the server stopped the robots but could not save the stopped state. */
    val persisted: Boolean,
    val robots: List<StopAllRobot>,
    /** True when the answer was built by stopping the robots one by one (the global call was rate-limited). */
    val viaFallback: Boolean = false,
) {
    /** Everything went as it should: no robot failed and the stopped state is saved. */
    val isClean: Boolean get() = success && failedCount == 0 && persisted

    /** The robots that did not stop. */
    val failedRobots: List<StopAllRobot> get() = robots.filter { !it.stopped }

    companion object {
        fun fromJson(json: JSONObject): StopAllResult {
            val list = json.optJSONArray("robots")
            val robots = (0 until (list?.length() ?: 0)).mapNotNull { i ->
                val item = list?.optJSONObject(i) ?: return@mapNotNull null
                StopAllRobot(
                    robotId = item.optInt("robotId"),
                    controllerId = item.optStr("controllerId"),
                    name = item.optStr("name"),
                    stopped = item.optBoolean("stopped", false),
                    valvesOff = item.optBoolean("valvesOff", false),
                    pumpsOff = item.optBoolean("pumpsOff", false),
                    reservedBy = item.optStr("reservedBy").ifEmpty { null },
                    error = item.optStr("error").ifEmpty { null },
                )
            }
            val failed = if (json.has("failedCount")) json.optInt("failedCount") else robots.count { !it.stopped }
            val stopped = if (json.has("stoppedCount")) json.optInt("stoppedCount") else robots.count { it.stopped }
            return StopAllResult(
                success = json.optBoolean("success", false),
                stoppedCount = stopped,
                failedCount = failed,
                persisted = json.optBoolean("persisted", false),
                robots = robots,
            )
        }
    }
}

/** What the global STOP button shows. */
sealed class StopAllStatus {
    /** The call is on the wire. */
    object Sending : StopAllStatus()

    /** The server answered; [result] says what happened robot by robot. */
    data class Done(val result: StopAllResult) : StopAllStatus()

    /** Nothing confirmed the stop; stays on screen until a later stop succeeds. [reason] is the cause, when known. */
    data class NotConfirmed(val reason: String?) : StopAllStatus()
}
