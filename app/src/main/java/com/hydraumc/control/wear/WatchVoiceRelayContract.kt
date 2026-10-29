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
 * server relays. The request carries the locale of the watch, but the server does
 * not translate: the fixed replies it composes itself (the status line, the
 * confirmation prompts) are in English today, and a reply of the language model
 * follows the locale only as far as the model does. So [text] is NOT guaranteed to
 * be in the language of the watch, and this phone does no translation either. The
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
) {
    fun toJson(): JSONObject = JSONObject()
        .put("type", "assistant_reply")
        .put("requestId", requestId)
        .put("text", text)
        .put("level", level)
        .put("speak", speak)
        .put("requiresConfirmation", requiresConfirmation)
        .apply { errorCode?.let { put("errorCode", it) } }

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
) {
    fun toJson(): JSONObject = JSONObject()
        .put("type", "system_status")
        .put("headline", headline)
        .put("detail", detail)
        .put("level", level)
        .put("speak", speak)
        .apply { errorCode?.let { put("errorCode", it) } }

    companion object {
        fun fromJson(json: JSONObject): WatchSystemStatus {
            require(json.getString("type") == "system_status") { "unexpected system status type" }
            return WatchSystemStatus(
                headline = json.getString("headline"),
                detail = json.getString("detail"),
                level = json.getString("level"),
                speak = json.getBoolean("speak"),
                errorCode = if (json.has("errorCode")) json.getString("errorCode") else null,
            )
        }
    }
}
