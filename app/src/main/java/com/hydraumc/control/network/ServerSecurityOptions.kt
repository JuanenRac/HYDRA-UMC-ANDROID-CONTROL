// =============================================================================
// HYDRA-UMC CONTROL - the access options of the server an administrator can change
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
// Seven switches of the server (GET/PUT /api/admin/server-config): the health route without a sign-in, the announcement of the
// global stop, the address kept in the history of connections, the camera pictures without a ticket, a token in the WebSocket
// address, the blanking of secrets in the log and the signature of a spoken confirmation. The reading is tolerant: a server that
// predates an option answers without it and the screen does not show that switch. A value fixed by the environment of the server
// cannot be changed from here, and every change that lowers the protection is asked about first.
package com.hydraumc.control.network

import org.json.JSONObject

/** Where the value of an option comes from. An [ENV] value is fixed by the environment of the server. */
enum class OptionSource { ENV, CONFIG, DEFAULT }

/** One switch as the server reports it. */
data class ServerOption(val value: Boolean, val source: OptionSource) {
    val locked: Boolean get() = source == OptionSource.ENV
}

/** The switches this app offers; each is null when the server does not report it. */
data class ServerSecurityOptions(
    val healthOpen: ServerOption?,
    val publishGlobalStop: ServerOption?,
    val recordAddress: ServerOption?,
    val cameraOpen: ServerOption?,
    val wsQueryToken: ServerOption?,
    val logRedaction: ServerOption?,
    val voiceSignature: ServerOption?,
) {
    /** True when the server reports none of them (a server that predates all of these options). */
    val isEmpty: Boolean
        get() = listOf(healthOpen, publishGlobalStop, recordAddress, cameraOpen, wsQueryToken, logRedaction, voiceSignature).all { it == null }

    /** The values as the screen starts with them; an option the server does not report keeps its safe value. */
    fun toValues(): ServerSecurityValues = ServerSecurityValues(
        healthOpen = healthOpen?.value ?: false,
        publishGlobalStop = publishGlobalStop?.value ?: false,
        recordAddress = recordAddress?.value ?: false,
        cameraOpen = cameraOpen?.value ?: false,
        wsQueryToken = wsQueryToken?.value ?: false,
        logRedaction = logRedaction?.value ?: true,
        voiceSignature = voiceSignature?.value ?: false,
    )
}

/** What the switches of the screen say. */
data class ServerSecurityValues(
    val healthOpen: Boolean,
    val publishGlobalStop: Boolean,
    val recordAddress: Boolean,
    val cameraOpen: Boolean,
    val wsQueryToken: Boolean,
    val logRedaction: Boolean,
    val voiceSignature: Boolean,
)

/** The changes that lower the protection of the server; the screen has a sentence for each. */
enum class ProtectionLowering { HEALTH_OPEN, RECORD_ADDRESS, CAMERA_OPEN, WS_QUERY_TOKEN, LOG_REDACTION_OFF, VOICE_SIGNATURE_OFF }

/** Why the options could not be read or saved, for the sentence the screen shows. */
enum class ServerOptionsFailure { UNAVAILABLE, SESSION, FORBIDDEN, NETWORK, OTHER }

object ServerSecurity {
    private fun option(json: JSONObject?): ServerOption? {
        json ?: return null
        if (!json.has("value") || json.opt("value") !is Boolean) return null
        val source = when (json.optString("source", "")) {
            "env" -> OptionSource.ENV
            "config" -> OptionSource.CONFIG
            "default" -> OptionSource.DEFAULT
            else -> return null
        }
        return ServerOption(json.getBoolean("value"), source)
    }

    /** Reads the answer of GET /api/admin/server-config; null when the text is not a JSON object. */
    fun parse(body: String): ServerSecurityOptions? {
        val root = try {
            JSONObject(body)
        } catch (_: Exception) {
            return null
        }
        return ServerSecurityOptions(
            healthOpen = option(root.optJSONObject("health")?.optJSONObject("open")),
            publishGlobalStop = option(root.optJSONObject("safety")?.optJSONObject("publishGlobalStop")),
            recordAddress = option(root.optJSONObject("connections")?.optJSONObject("recordAddress")),
            cameraOpen = option(root.optJSONObject("camera")?.optJSONObject("open")),
            wsQueryToken = option(root.optJSONObject("websocket")?.optJSONObject("allowQueryToken")),
            logRedaction = option(root.optJSONObject("logging")?.optJSONObject("redactSecrets")),
            voiceSignature = option(root.optJSONObject("voice")?.optJSONObject("requireSignature")),
        )
    }

    /**
     * The request body: only what changed, and never an option the server fixes from its environment or does not report. Null when
     * nothing would be sent.
     */
    fun patch(current: ServerSecurityOptions, next: ServerSecurityValues): JSONObject? {
        val body = JSONObject()
        fun put(group: String, name: String, option: ServerOption?, wanted: Boolean) {
            if (option == null || option.locked || option.value == wanted) return
            val node = body.optJSONObject(group) ?: JSONObject().also { body.put(group, it) }
            node.put(name, wanted)
        }
        put("health", "open", current.healthOpen, next.healthOpen)
        put("safety", "publishGlobalStop", current.publishGlobalStop, next.publishGlobalStop)
        put("connections", "recordAddress", current.recordAddress, next.recordAddress)
        put("camera", "open", current.cameraOpen, next.cameraOpen)
        put("websocket", "allowQueryToken", current.wsQueryToken, next.wsQueryToken)
        put("logging", "redactSecrets", current.logRedaction, next.logRedaction)
        put("voice", "requireSignature", current.voiceSignature, next.voiceSignature)
        return if (body.length() == 0) null else body
    }

    /**
     * The changes in [next] that lower the protection, in a fixed order. Turning a switch to its safe side never asks, and neither does
     * the announcement of the global stop (announcing a stop is the safe direction). An option the server fixes is not a change.
     */
    fun lowerings(current: ServerSecurityOptions, next: ServerSecurityValues): List<ProtectionLowering> {
        fun rises(option: ServerOption?, wanted: Boolean) = option != null && !option.locked && !option.value && wanted
        fun falls(option: ServerOption?, wanted: Boolean) = option != null && !option.locked && option.value && !wanted
        return buildList {
            if (rises(current.healthOpen, next.healthOpen)) add(ProtectionLowering.HEALTH_OPEN)
            if (rises(current.recordAddress, next.recordAddress)) add(ProtectionLowering.RECORD_ADDRESS)
            if (rises(current.cameraOpen, next.cameraOpen)) add(ProtectionLowering.CAMERA_OPEN)
            if (rises(current.wsQueryToken, next.wsQueryToken)) add(ProtectionLowering.WS_QUERY_TOKEN)
            if (falls(current.logRedaction, next.logRedaction)) add(ProtectionLowering.LOG_REDACTION_OFF)
            if (falls(current.voiceSignature, next.voiceSignature)) add(ProtectionLowering.VOICE_SIGNATURE_OFF)
        }
    }
}
