// =============================================================================
// HYDRA-UMC CONTROL - The fault record of the server and the health of its boards
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
// Both are read only. GET /api/faults gives the faults raised and not cleared and the recent history; the server has it
// only with its fault feed on. GET /api/ecosystem/node-health?slot=N gives the last health frame of one controller board
// (slots 0 to 7); it is off until an administrator turns it on, and only operators and administrators may ask.
package com.hydraumc.control.network

import org.json.JSONArray
import org.json.JSONObject

/** One line of the fault record. [atMs] is when it was raised (an active fault) or recorded (the history). */
data class ServerFault(
    val source: String,
    val robotId: String?,
    val code: String,
    /** "info", "warning", "error" or "critical"; an unknown word is read as "error". */
    val severity: String,
    val text: String,
    val cleared: Boolean,
    val atMs: Long,
)

data class FaultRecord(val active: List<ServerFault>, val recent: List<ServerFault>)

object ServerFaults {
    /** How many lines of history the app asks for. */
    const val LIMIT = 50
    private const val TEXT_MAX = 240
    private val SEVERITIES = setOf("info", "warning", "error", "critical")

    private fun text(json: JSONObject, name: String): String = if (!json.has(name) || json.isNull(name)) "" else json.optString(name, "")

    private fun one(json: JSONObject?, timeField: String): ServerFault? {
        json ?: return null
        val source = text(json, "source").take(64)
        val code = text(json, "code").take(64)
        if (source.isEmpty() || code.isEmpty()) return null
        return ServerFault(
            source = source,
            robotId = text(json, "robotId").take(64).ifEmpty { null },
            code = code,
            severity = text(json, "severity").takeIf { it in SEVERITIES } ?: "error",
            text = text(json, "text").take(TEXT_MAX),
            cleared = text(json, "state") == "cleared",
            atMs = json.optLong(timeField, 0L),
        )
    }

    private fun list(array: JSONArray?, timeField: String): List<ServerFault> =
        if (array == null) emptyList() else (0 until array.length()).mapNotNull { one(array.optJSONObject(it), timeField) }

    /** The answer of GET /api/faults, newest first; null when the text is not that answer. What cannot be read is left out. */
    fun parse(body: String): FaultRecord? {
        val json = runCatching { JSONObject(body) }.getOrNull() ?: return null
        val active = json.optJSONArray("active") ?: return null
        return FaultRecord(
            active = list(active, "since").sortedByDescending { it.atMs },
            recent = list(json.optJSONArray("recent"), "at").sortedByDescending { it.atMs }.take(LIMIT),
        )
    }
}

/** One controller board as the brain of the server last heard it. */
data class BoardHealth(
    val slot: Int,
    /** False when the brain did not answer the query. */
    val online: Boolean,
    /** False when the brain has never heard a health frame of the slot. */
    val heard: Boolean,
    val ageMs: Long?,
    val flags: List<String>,
    val txErrors: Long?,
    val rxErrors: Long?,
    val stalls: Long?,
    val uptimeS: Long?,
)

/** The slots a server can have. */
val BOARD_SLOTS = 0..7

/** Why one of the two readings is not there. */
enum class ReadingProblem { OFF, FORBIDDEN, SESSION, UNAVAILABLE, NETWORK }

fun readingProblemOf(error: HydraApiException): ReadingProblem = when {
    error.isSessionProblem -> ReadingProblem.SESSION
    error.httpCode == 403 -> ReadingProblem.FORBIDDEN
    error.httpCode == 404 -> ReadingProblem.OFF
    error.httpCode != null -> ReadingProblem.UNAVAILABLE
    else -> ReadingProblem.NETWORK
}
