// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - Authenticated Watch voice relay wire contract
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.wear

import org.json.JSONObject

/** Bounded recognised text received from the future Wear transport. */
data class WatchVoiceTurn(
    val requestId: String,
    val transcript: String,
    val locale: String,
) {
    init {
        require(REQUEST_ID.matches(requestId)) { "requestId must contain 1-64 letters, digits, _ or -" }
        require(transcript.isNotBlank() && transcript.length <= 500) { "transcript must contain 1-500 characters" }
        require(locale.length in 2..35) { "locale must contain 2-35 characters" }
    }

    fun toJson(): JSONObject = JSONObject()
        .put("type", "voice_turn")
        .put("requestId", requestId)
        .put("transcript", transcript.trim())
        .put("locale", locale)

    companion object {
        private val REQUEST_ID = Regex("^[A-Za-z0-9_-]{1,64}$")

        fun fromJson(json: JSONObject): WatchVoiceTurn = WatchVoiceTurn(
            requestId = json.getString("requestId"),
            transcript = json.getString("transcript"),
            locale = json.getString("locale"),
        ).also { require(json.getString("type") == "voice_turn") { "unexpected Watch message type" } }
    }
}

/**
 * Safe reply returned by Server after it relays a Watch turn to Voice UI.
 *
 * [errorCode] is a stable, NEVER-translated protocol identifier for
 * this relay's own SYSTEM fallback replies (WatchVoiceRelayService's own
 * catch block below, generated locally on the phone, in English, with no
 * access to the watch's own locale) - as opposed to [text], which is whatever the
 * server relays. The request carries the locale of the watch, and the server and the
 * voice service write their own fixed replies (the status line, the confirmation
 * prompts) in that language, naming it in the reply they send. A reply of the language
 * model follows the locale only as far as the model does, so [text] is still not
 * guaranteed to be in the language of the watch, and this phone does no translation. The
 * watch (not this phone) resolves a known [errorCode] to a real localized
 * string from its own strings.xml (see HYDRA-UMC-WATCH's own
 * errorCodeToStringRes()) instead of ever speaking/showing this phone's
 * own English [text] fallback. `null` means "trust [text] as-is", which
 * covers every real AI reply.
 */
data class WatchAssistantReply(
    val requestId: String,
    val text: String,
    val level: String,
    val speak: Boolean,
    val requiresConfirmation: Boolean,
    val errorCode: String? = null,
    /**
     * The one-use token that completes a reply with [requiresConfirmation] (two taps on the watch), when the server sends one.
     * It is passed on as it came and never logged; the watch holds it in memory for [confirmationValiditySeconds] at most.
     */
    val confirmationToken: String? = null,
    val confirmationValiditySeconds: Int? = null,
) {
    init {
        require(confirmationToken == null || WatchVoiceConfirm.isValidToken(confirmationToken)) { "invalid confirmation token" }
        require(confirmationValiditySeconds == null || confirmationValiditySeconds in 1..com.hydraumc.control.network.RobotCommandContract.VOICE_CONFIRM_VALIDITY_MAX_SECONDS) {
            "confirmation validity must be from 1 to ${com.hydraumc.control.network.RobotCommandContract.VOICE_CONFIRM_VALIDITY_MAX_SECONDS} seconds"
        }
    }

    fun toJson(): JSONObject = JSONObject()
        .put("type", "assistant_reply")
        .put("requestId", requestId)
        .put("text", text)
        .put("level", level)
        .put("speak", speak)
        .put("requiresConfirmation", requiresConfirmation)
        .apply { errorCode?.let { put("errorCode", it) } }
        .apply { confirmationToken?.let { put("confirmationToken", it) } }
        .apply { confirmationValiditySeconds?.let { put("confirmationValiditySeconds", it) } }

    companion object {
        fun fromJson(json: JSONObject): WatchAssistantReply {
            require(json.getString("type") == "assistant_reply") { "unexpected voice response type" }
            return WatchAssistantReply(
                requestId = json.getString("requestId"),
                text = json.getString("text"),
                level = json.getString("level"),
                speak = json.getBoolean("speak"),
                requiresConfirmation = json.getBoolean("requiresConfirmation"),
                errorCode = if (json.has("errorCode")) json.getString("errorCode") else null,
                // A token or validity the watch would refuse is dropped, not passed on: the reply itself still arrives.
                confirmationToken = json.optString("confirmationToken", "").takeIf { it.isNotEmpty() && WatchVoiceConfirm.isValidToken(it) },
                confirmationValiditySeconds = if (json.has("confirmationValiditySeconds")) {
                    json.optInt("confirmationValiditySeconds", 0).takeIf { it in 1..com.hydraumc.control.network.RobotCommandContract.VOICE_CONFIRM_VALIDITY_MAX_SECONDS }
                } else {
                    null
                },
            )
        }
    }
}

/**
 * Read-only security status of the server for the watch: which options are on a value that lowers the protection, how many the
 * environment of the server fixes, the clients with an active session and the failed sign-ins. Nothing in it can change anything on
 * the server, and the watch has no message that writes. Same rules as the watch applies when it receives it
 * (docs/watch_relay_contract.json, "server_security_status").
 */
data class WatchServerSecurityStatus(
    val loweredSwitches: List<String>,
    val lockedSwitches: Int,
    val activeClients: Int,
    val failedLogins: Int,
    val protocolVersion: Int = PROTOCOL_VERSION,
) {
    init {
        require(protocolVersion == PROTOCOL_VERSION) { "unsupported security status protocol version" }
        require(loweredSwitches.size <= MAX_SWITCHES && loweredSwitches.toSet().size == loweredSwitches.size) { "at most $MAX_SWITCHES different switches" }
        require(loweredSwitches.all { SWITCH_ID.matches(it) }) { "a switch id is section.key" }
        require(listOf(lockedSwitches, activeClients, failedLogins).all { it in 0..MAX_COUNT }) { "counts must be from 0 to $MAX_COUNT" }
    }

    fun toJson(): JSONObject = JSONObject()
        .put("type", "server_security_status")
        .put("protocolVersion", protocolVersion)
        .put("loweredSwitches", org.json.JSONArray(loweredSwitches))
        .put("lockedSwitches", lockedSwitches)
        .put("activeClients", activeClients)
        .put("failedLogins", failedLogins)

    companion object {
        const val PROTOCOL_VERSION = 1
        const val MAX_SWITCHES = 16
        const val MAX_COUNT = 100_000
        private val SWITCH_ID = Regex("^[A-Za-z]{1,24}\\.[A-Za-z]{1,32}$")

        fun fromJson(json: JSONObject): WatchServerSecurityStatus {
            require(json.getString("type") == "server_security_status") { "unexpected security status type" }
            val list = json.optJSONArray("loweredSwitches")
            return WatchServerSecurityStatus(
                loweredSwitches = if (list == null) emptyList() else (0 until list.length()).map { list.getString(it) },
                lockedSwitches = json.optInt("lockedSwitches", 0),
                activeClients = json.optInt("activeClients", 0),
                failedLogins = json.optInt("failedLogins", 0),
                protocolVersion = json.getInt("protocolVersion"),
            )
        }
    }
}

/**
 * Read-only system-health card that can later be forwarded to the watch.
 * [errorCode] is the same mechanism as [WatchAssistantReply]'s own -
 * see that field's header comment.
 */
data class WatchSystemStatus(
    val headline: String,
    val detail: String,
    val level: String,
    val speak: Boolean,
    val errorCode: String? = null,
    /** How many robots the phone sees running (0 to 10000), or null when it does not say: the complication of the watch shows it. */
    val robotsRunning: Int? = null,
    /** Whether the phone sees an alarm on the plant, or null when it does not say. */
    val alarmActive: Boolean? = null,
) {
    init {
        require(robotsRunning == null || robotsRunning in 0..10000) { "robotsRunning must be from 0 to 10000" }
    }

    fun toJson(): JSONObject = JSONObject()
        .put("type", "system_status")
        .put("headline", headline)
        .put("detail", detail)
        .put("level", level)
        .put("speak", speak)
        .apply { errorCode?.let { put("errorCode", it) } }
        .apply { robotsRunning?.let { put("robotsRunning", it) } }
        .apply { alarmActive?.let { put("alarmActive", it) } }

    companion object {
        fun fromJson(json: JSONObject): WatchSystemStatus {
            require(json.getString("type") == "system_status") { "unexpected system status type" }
            return WatchSystemStatus(
                headline = json.getString("headline"),
                detail = json.getString("detail"),
                level = json.getString("level"),
                speak = json.getBoolean("speak"),
                errorCode = if (json.has("errorCode")) json.getString("errorCode") else null,
                robotsRunning = if (json.has("robotsRunning")) json.getInt("robotsRunning") else null,
                alarmActive = if (json.has("alarmActive")) json.getBoolean("alarmActive") else null,
            )
        }
    }
}

/**
 * Read-only progress of a flash cycle of a board for the watch: the phase (one of [com.hydraumc.control.model.CanOtaPhase]'s wire names) and a percent
 * from 0 to 100, or none. The phone reads it from the server (`canota_progress`, sent only to administrators) and passes it on; nothing in it can start
 * or stop a flash. Same rules as the watch applies when it receives it (docs/watch_relay_contract.json, "canota_progress_status").
 */
data class WatchCanOtaProgress(
    val phase: String,
    val percent: Int?,
    val protocolVersion: Int = PROTOCOL_VERSION,
) {
    init {
        require(protocolVersion == PROTOCOL_VERSION) { "unsupported flash progress protocol version" }
        require(com.hydraumc.control.model.CanOtaPhase.fromWire(phase) != null) { "a phase of a flash cycle" }
        require(percent == null || percent in 0..100) { "percent must be from 0 to 100" }
    }

    fun toJson(): JSONObject = JSONObject()
        .put("type", "canota_progress_status")
        .put("protocolVersion", protocolVersion)
        .put("phase", phase)
        .apply { percent?.let { put("percent", it) } }

    companion object {
        const val PROTOCOL_VERSION = 1

        fun fromJson(json: JSONObject): WatchCanOtaProgress {
            require(json.getString("type") == "canota_progress_status") { "unexpected flash progress type" }
            return WatchCanOtaProgress(
                phase = json.getString("phase"),
                percent = if (json.has("percent")) json.getInt("percent") else null,
                protocolVersion = json.getInt("protocolVersion"),
            )
        }

        /** The message for [progress], or null when it has nothing the watch accepts. */
        fun of(progress: com.hydraumc.control.model.CanOtaProgress): WatchCanOtaProgress =
            WatchCanOtaProgress(progress.phase.wire, progress.percent)
    }
}
