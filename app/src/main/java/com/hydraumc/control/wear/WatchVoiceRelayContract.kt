// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - Authenticated Watch voice relay wire contract
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0 - see LICENSE
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

/** Safe reply returned by Server after it relays a Watch turn to Voice UI. */
data class WatchAssistantReply(
    val requestId: String,
    val text: String,
    val level: String,
    val speak: Boolean,
    val requiresConfirmation: Boolean,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("type", "assistant_reply")
        .put("requestId", requestId)
        .put("text", text)
        .put("level", level)
        .put("speak", speak)
        .put("requiresConfirmation", requiresConfirmation)

    companion object {
        fun fromJson(json: JSONObject): WatchAssistantReply {
            require(json.getString("type") == "assistant_reply") { "unexpected voice response type" }
            return WatchAssistantReply(
                requestId = json.getString("requestId"),
                text = json.getString("text"),
                level = json.getString("level"),
                speak = json.getBoolean("speak"),
                requiresConfirmation = json.getBoolean("requiresConfirmation"),
            )
        }
    }
}

/** Read-only system-health card that can later be forwarded to the watch. */
data class WatchSystemStatus(
    val headline: String,
    val detail: String,
    val level: String,
    val speak: Boolean,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("type", "system_status")
        .put("headline", headline)
        .put("detail", detail)
        .put("level", level)
        .put("speak", speak)

    companion object {
        fun fromJson(json: JSONObject): WatchSystemStatus {
            require(json.getString("type") == "system_status") { "unexpected system status type" }
            return WatchSystemStatus(
                headline = json.getString("headline"),
                detail = json.getString("detail"),
                level = json.getString("level"),
                speak = json.getBoolean("speak"),
            )
        }
    }
}
