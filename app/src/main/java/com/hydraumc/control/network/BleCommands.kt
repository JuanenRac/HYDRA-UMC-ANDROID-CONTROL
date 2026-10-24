// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - BLE commands and their acknowledgements
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.network

import org.json.JSONException
import org.json.JSONObject

/**
 * One robot command to send over Bluetooth LE (see docs/BLE_CONTRACT.md): the body of
 * `POST /api/robot/{id}/command` with the robot id, an `id` the answer will carry back and the access token of the
 * account. The peripheral hands it to the same route a Wi-Fi client uses, so it gets no privilege of its own.
 */
data class BleCommandRequest(
    val robotId: Int,
    val command: String,
    val params: JSONObject = JSONObject(),
    val controllerId: String? = null,
    val token: String,
)

/** The acknowledgement of the peripheral. `ok` only when the route of the server answered with a 2xx status. */
data class BleAck(
    /** The id of the command; null when the peripheral could not read it (a damaged message). */
    val id: String?,
    val ok: Boolean,
    /** The HTTP status of the route; 0 when the command never reached it. */
    val status: Int,
    /** A stable word: `ok`, `claimed`, `forbidden`, `rate-limited`, `commands-disabled`, `unreachable`... */
    val code: String,
    val error: String?,
)

/** What became of a command sent over Bluetooth. Only [Acknowledged] says anything about the robot. */
sealed class BleCommandOutcome {
    /** The peripheral answered; look at [BleAck.ok]. */
    data class Acknowledged(val ack: BleAck) : BleCommandOutcome()
    /** Nothing answered in time or the link dropped: the command must be treated as NOT delivered. */
    object NoAnswer : BleCommandOutcome()
    /** The link was not ready, or the device offers no ACK characteristic: nothing was sent. */
    object NotAvailable : BleCommandOutcome()
    /** The command did not fit in one message (64 KiB): nothing was sent. */
    object TooLarge : BleCommandOutcome()
}

object BleCommands {
    /** Longest `id` the peripheral accepts. */
    const val MAX_ID_CHARS = 64

    /** How long a command waits for its acknowledgement before it counts as not delivered. */
    const val DEFAULT_ACK_TIMEOUT_MS = 4_000L

    /** The message the peripheral reads: UTF-8 JSON. */
    fun encode(id: String, request: BleCommandRequest): ByteArray {
        val body = JSONObject()
            .put("id", id)
            .put("robotId", request.robotId)
            .put("command", request.command)
            .put("params", request.params)
            .put("token", request.token)
        if (!request.controllerId.isNullOrEmpty()) body.put("controllerId", request.controllerId)
        return body.toString().toByteArray(Charsets.UTF_8)
    }

    /** The acknowledgement in a message of the ACK characteristic; null when it is not one (never throws). */
    fun parseAck(bytes: ByteArray): BleAck? {
        val json = try {
            JSONObject(String(bytes, Charsets.UTF_8))
        } catch (_: JSONException) {
            return null
        }
        if (!json.has("ok") || json.opt("ok") !is Boolean) return null
        val id = json.opt("id") as? String
        val status = (json.opt("status") as? Number)?.toInt() ?: 0
        val code = (json.opt("code") as? String).orEmpty()
        val error = (json.opt("error") as? String)?.takeIf { it.isNotEmpty() }
        return BleAck(id = id, ok = json.getBoolean("ok"), status = status, code = code, error = error)
    }
}
