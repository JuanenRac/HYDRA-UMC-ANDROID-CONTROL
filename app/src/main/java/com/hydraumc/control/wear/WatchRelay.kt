// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - wear/WatchRelay.kt
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.wear

import com.hydraumc.control.model.HydraState
import com.hydraumc.control.network.HydraApiClient
import com.hydraumc.control.network.HydraApiException
import com.hydraumc.control.network.RobotCommandContract
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

/** Sends one message to a node of the Wearable Data Layer. The service gives the real one; a test gives a recorder. */
fun interface MessageSender {
    fun send(nodeId: String, path: String, payload: ByteArray)
}

/** What the relay asks of the server, with the session already in place. */
interface WatchRelayBackend {
    suspend fun voiceTurn(turn: WatchVoiceTurn): WatchAssistantReply
    suspend fun systemStatus(): WatchSystemStatus
    suspend fun serverSecurity(): WatchServerSecurityStatus?
    suspend fun presence(body: JSONObject)
    suspend fun voiceConfirm(confirm: WatchVoiceConfirm): WatchAssistantReply
}

/** The switches the operator sets in the app. */
interface WatchRelayFlags {
    suspend fun presenceRelay(): Boolean
    suspend fun voiceConfirmRelay(): Boolean
}

/** The texts the relay writes itself, in the language of the phone. */
data class WatchRelayTexts(
    val connectionUnavailable: String,
    val offlineHeadline: String,
    val offlineDetail: String,
    val confirmRelayOff: String,
)

/** How long a single relayed request may run before it is treated as failed. */
const val WATCH_RELAY_REQUEST_TIMEOUT_MS = 15_000L

/**
 * What the phone does with each message of the watch: the five routes (spoken turn, system status, server security, presence,
 * spoken confirmation). It owns no Android type: the answers go out through [sender], the server is reached through [backend].
 */
class WatchRelayHandler(
    private val requests: BoundedRequestScope,
    private val backend: WatchRelayBackend,
    private val sender: MessageSender,
    private val flags: WatchRelayFlags,
    private val texts: WatchRelayTexts,
    private val presenceTracker: WatchPresenceTracker = WatchPresenceTracker(),
    private val requestTimeoutMs: Long = WATCH_RELAY_REQUEST_TIMEOUT_MS,
    private val onLog: (String) -> Unit = {},
) {
    @Volatile
    var presenceUnsupported = false
        private set

    /** Routes one message; an unknown path or a body that does not parse is dropped. */
    fun onMessage(path: String, sourceNodeId: String, data: ByteArray) {
        when (path) {
            WatchRelayPaths.VOICE_TURN -> voiceTurn(sourceNodeId, data)
            WatchRelayPaths.STATUS_REQUEST -> systemStatus(sourceNodeId)
            WatchRelayPaths.SERVER_SECURITY_REQUEST -> serverSecurity(sourceNodeId)
            WatchBridgePaths.PRESENCE -> presence(data)
            WatchBridgePaths.VOICE_CONFIRM -> voiceConfirm(sourceNodeId, data)
            else -> Unit
        }
    }

    /** A request replaced or cancelled must stop quietly: it sends no reply that would race the one of its successor. */
    private fun Throwable.rethrowIfCancelled() {
        if (this is kotlinx.coroutines.CancellationException) throw this
    }

    private fun reply(nodeId: String, path: String, json: JSONObject) = sender.send(nodeId, path, json.toString().encodeToByteArray())

    private fun unavailableReply(requestId: String) = WatchAssistantReply(
        requestId = requestId,
        text = texts.connectionUnavailable,
        level = "ATTENTION",
        speak = true,
        requiresConfirmation = false,
        errorCode = "connection_unavailable",
    )

    private fun offlineStatus() = WatchSystemStatus(
        headline = texts.offlineHeadline,
        detail = texts.offlineDetail,
        level = "OFFLINE",
        speak = false,
        errorCode = "offline",
    )

    private fun voiceTurn(nodeId: String, data: ByteArray) {
        val turn = runCatching { WatchVoiceTurn.fromJson(JSONObject(data.decodeToString())) }.getOrNull() ?: return
        // Keyed by the id of the turn: a repeated delivery replaces the attempt still running instead of racing it.
        requests.launch(turn.requestId) {
            val answer = runCatching {
                withTimeoutOrNull(requestTimeoutMs) { backend.voiceTurn(turn) } ?: error("voice turn request timed out")
            }.getOrElse { it.rethrowIfCancelled(); unavailableReply(turn.requestId) }
            reply(nodeId, WatchRelayPaths.ASSISTANT_REPLY, answer.toJson())
        }
    }

    private fun systemStatus(nodeId: String) {
        // Keyed by the node: a burst of taps from one watch keeps one request in flight.
        requests.launch("status:$nodeId") {
            val status = runCatching {
                withTimeoutOrNull(requestTimeoutMs) { backend.systemStatus() } ?: error("system status request timed out")
            }.getOrElse { it.rethrowIfCancelled(); offlineStatus() }
            reply(nodeId, WatchRelayPaths.SYSTEM_STATUS, status.toJson())
        }
    }

    private fun serverSecurity(nodeId: String) {
        requests.launch("security:$nodeId") {
            val status = runCatching { withTimeoutOrNull(requestTimeoutMs) { backend.serverSecurity() } }.getOrElse { it.rethrowIfCancelled(); null }
            if (status != null) {
                reply(nodeId, WatchRelayPaths.SERVER_SECURITY, status.toJson())
            } else {
                reply(nodeId, WatchRelayPaths.SYSTEM_STATUS, offlineStatus().toJson())
            }
        }
    }

    private fun presence(data: ByteArray) {
        val beat = runCatching { WatchPresenceHeartbeat.fromJson(JSONObject(data.decodeToString())) }.getOrNull() ?: return
        requests.launch("presence") {
            if (!flags.presenceRelay()) return@launch
            val missed = presenceTracker.onHeartbeat(beat)
            if (presenceUnsupported) return@launch
            try {
                withTimeoutOrNull(RobotCommandContract.PRESENCE_RELAY_TIMEOUT_MS) { backend.presence(beat.toServerJson(missed)) }
            } catch (e: HydraApiException) {
                if (e.isNotFound) presenceUnsupported = true
                onLog("presence relay refused: HTTP ${e.httpCode}")
            } catch (e: Exception) {
                e.rethrowIfCancelled()
                onLog("presence relay failed")
            }
        }
    }

    private fun voiceConfirm(nodeId: String, data: ByteArray) {
        val confirm = runCatching { WatchVoiceConfirm.fromJson(JSONObject(data.decodeToString())) }.getOrNull() ?: return
        requests.launch(confirm.requestId) {
            val answer = if (!flags.voiceConfirmRelay()) {
                WatchAssistantReply(confirm.requestId, texts.confirmRelayOff, "ATTENTION", speak = false, requiresConfirmation = false)
            } else {
                runCatching {
                    withTimeoutOrNull(RobotCommandContract.VOICE_CONFIRM_TIMEOUT_MS) { backend.voiceConfirm(confirm) }
                        ?: error("voice confirmation timed out")
                }.getOrElse { e ->
                    e.rethrowIfCancelled()
                    onLog("voice confirmation relay failed (${e.javaClass.simpleName})")
                    unavailableReply(confirm.requestId)
                }
            }
            reply(nodeId, WatchRelayPaths.ASSISTANT_REPLY, answer.toJson())
        }
    }
}

/** The backend that talks to the real server; [client] gives a client with the stored session, or throws when there is none. */
class ClientRelayBackend(private val client: suspend () -> HydraApiClient) : WatchRelayBackend {
    override suspend fun voiceTurn(turn: WatchVoiceTurn) = client().postWatchVoiceTurn(turn)

    override suspend fun systemStatus(): WatchSystemStatus {
        val api = client()
        val reply = api.getWatchSystemStatus()
        // The complication shows the robots running and the alarm; a failure of the settings only leaves them out.
        val plant = runCatching { HydraState(api.getSettings()) }.getOrNull() ?: return reply
        return reply.copy(robotsRunning = WatchPlantSummary.robotsRunning(plant), alarmActive = WatchPlantSummary.alarmActive(plant))
    }

    override suspend fun serverSecurity(): WatchServerSecurityStatus? {
        val api = client()
        return WatchServerSecurity.fromAnswers(api.getServerConfigBody(), api.getConnectionClientsBody())
    }

    override suspend fun presence(body: JSONObject) {
        client().postPresence(body)
    }

    override suspend fun voiceConfirm(confirm: WatchVoiceConfirm) = client().postVoiceConfirm(confirm)
}
