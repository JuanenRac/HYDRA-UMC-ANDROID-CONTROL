// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - Wear OS voice relay service
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
// Receives bounded recognised text/status requests from the paired Watch via
// Google Play services Data Layer. The package/signature boundary protects
// this channel; the Server JWT remains encrypted only on the phone.
//
// Found while auditing the code: this
// used to launch every request on a CoroutineScope that was never cancelled
// in onDestroy(), with no concurrency bound and no timeout per request, and
// silently dropped any sendMessage() failure. Fixed: onDestroy() now cancels
// everything real (see BoundedRequestScope.kt for the actual bounding logic,
// unit-tested on its own), each request gets a real bounded timeout instead
// of running forever, and a failed sendMessage() is at least logged instead
// of vanishing.
package com.hydraumc.control.wear

import android.util.Log
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import com.hydraumc.control.R
import com.hydraumc.control.network.AuthPrefs
import com.hydraumc.control.sharedServicesOrNull
import com.hydraumc.control.network.ConnectionPrefs
import com.hydraumc.control.model.HydraState
import com.hydraumc.control.network.HydraApiClient
import com.hydraumc.control.network.HydraApiException
import com.hydraumc.control.network.RobotCommandContract
import com.hydraumc.control.network.TlsPrefs
import com.hydraumc.control.network.TlsSettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

object WatchRelayPaths {
    const val VOICE_TURN = "/hydra-umc/voice-turn/v1"
    const val STATUS_REQUEST = "/hydra-umc/system-status/v1"
    const val ASSISTANT_REPLY = "/hydra-umc/assistant-reply/v1"
    const val SYSTEM_STATUS = "/hydra-umc/system-status-reply/v1"
    /** Watch -> phone: asks for the read-only security status of the server. */
    const val SERVER_SECURITY_REQUEST = "/hydra-umc/server-security-request/v1"
    /** Phone -> watch: the answer. */
    const val SERVER_SECURITY = "/hydra-umc/server-security/v1"
}

private const val TAG = "WatchVoiceRelay"

/** How long a single relayed request may run before it's treated as failed -
 * a hung phone/Server round trip must not hold a slot (or, before the
 * fix, the whole service) open indefinitely. */
private const val REQUEST_TIMEOUT_MS = 15_000L

class WatchVoiceRelayService : WearableListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val requests = BoundedRequestScope(scope)
    private val presenceTracker = WatchPresenceTracker()
    @Volatile private var presenceUnsupported = false

    override fun onDestroy() {
        // The real fix: previously nothing here at all, so every
        // in-flight (or future, already-queued) coroutine from this scope
        // kept running past a real service recreation.
        requests.cancelAll()
        scope.cancel()
        super.onDestroy()
    }

    override fun onMessageReceived(event: MessageEvent) {
        when (event.path) {
            WatchRelayPaths.VOICE_TURN -> relayVoiceTurn(event)
            WatchRelayPaths.STATUS_REQUEST -> relaySystemStatus(event)
            WatchRelayPaths.SERVER_SECURITY_REQUEST -> relayServerSecurity(event)
            WatchBridgePaths.PRESENCE -> relayPresence(event)
            WatchBridgePaths.VOICE_CONFIRM -> relayVoiceConfirm(event)
            else -> Unit
        }
    }

    private fun relayVoiceTurn(event: MessageEvent) {
        val turn = runCatching { WatchVoiceTurn.fromJson(JSONObject(event.data.decodeToString())) }.getOrNull()
            ?: return
        // Keyed by the turn's own requestId - a duplicate/retried Data Layer
        // delivery for the same requestId replaces the still-running attempt
        // instead of racing it to send two replies.
        requests.launch(turn.requestId) {
            val reply = runCatching {
                withTimeoutOrNull(REQUEST_TIMEOUT_MS) { authenticatedClient().postWatchVoiceTurn(turn) }
                    ?: error("voice turn request timed out after ${REQUEST_TIMEOUT_MS}ms")
            }.getOrElse {
                // The text is in the language of this phone, for a watch build too old to
                // recognize errorCode; a current watch resolves errorCode in its own language.
                WatchAssistantReply(
                    requestId = turn.requestId,
                    text = getString(R.string.watch_relay_connection_unavailable),
                    level = "ATTENTION",
                    speak = true,
                    requiresConfirmation = false,
                    errorCode = "connection_unavailable",
                )
            }
            send(event.sourceNodeId, WatchRelayPaths.ASSISTANT_REPLY, reply.toJson().toString())
        }
    }

    private fun relaySystemStatus(event: MessageEvent) {
        // This message carries no requestId of its own - keyed by source
        // node instead, so a burst of status taps from the same Watch still
        // only ever has one real request in flight at a time.
        requests.launch("status:${event.sourceNodeId}") {
            val status = runCatching {
                withTimeoutOrNull(REQUEST_TIMEOUT_MS) {
                    val client = authenticatedClient()
                    val reply = client.getWatchSystemStatus()
                    // The complication of the watch shows the robots running and the alarm; they come from the settings the
                    // phone reads (a failure only leaves them out, the status itself still arrives).
                    val plant = runCatching { HydraState(client.getSettings()) }.getOrNull()
                    if (plant == null) reply else reply.copy(robotsRunning = WatchPlantSummary.robotsRunning(plant), alarmActive = WatchPlantSummary.alarmActive(plant))
                } ?: error("system status request timed out after ${REQUEST_TIMEOUT_MS}ms")
            }.getOrElse {
                // same mechanism as relayVoiceTurn's own fallback
                // above - headline/detail stay as an honest fallback for
                // an old watch build, errorCode is what a current one
                // actually resolves and shows/speaks.
                WatchSystemStatus(
                    headline = getString(R.string.watch_status_offline_headline),
                    detail = getString(R.string.watch_status_offline_detail),
                    level = "OFFLINE",
                    speak = false,
                    errorCode = "offline",
                )
            }
            send(event.sourceNodeId, WatchRelayPaths.SYSTEM_STATUS, status.toJson().toString())
        }
    }

    private fun relayServerSecurity(event: MessageEvent) {
        // Keyed by source node like the status: a burst of taps has one request in flight. Read only: the phone reads the options and the
        // history as the administrator that is signed in, and the watch gets a summary; an account that may not read them (the server
        // answers 403), or a server that does not answer, gets the same "offline" card as a failed status.
        requests.launch("security:${event.sourceNodeId}") {
            val status = runCatching {
                withTimeoutOrNull(REQUEST_TIMEOUT_MS) {
                    val client = authenticatedClient()
                    WatchServerSecurity.fromAnswers(client.getServerConfigBody(), client.getConnectionClientsBody())
                } ?: error("security status request failed or timed out")
            }.getOrNull()
            if (status != null) {
                send(event.sourceNodeId, WatchRelayPaths.SERVER_SECURITY, status.toJson().toString())
            } else {
                val offline = WatchSystemStatus(
                    headline = getString(R.string.watch_status_offline_headline),
                    detail = getString(R.string.watch_status_offline_detail),
                    level = "OFFLINE",
                    speak = false,
                    errorCode = "offline",
                )
                send(event.sourceNodeId, WatchRelayPaths.SYSTEM_STATUS, offline.toJson().toString())
            }
        }
    }

    /**
     * Passes the presence heartbeat of the watch to the server, only while the operator switched the relay on in the app. A heartbeat
     * is never retried (the next one is the retry) and the server decides what a lost one means. A server without the route answers
     * 404: the relay then stays quiet until the service is created again.
     */
    private fun relayPresence(event: MessageEvent) {
        val beat = runCatching { WatchPresenceHeartbeat.fromJson(JSONObject(event.data.decodeToString())) }.getOrNull() ?: return
        requests.launch("presence") {
            if (!ConnectionPrefs(applicationContext).loadFlag(ConnectionPrefs.Flags.PRESENCE_RELAY, RobotCommandContract.PRESENCE_RELAY_ENABLED_BY_DEFAULT)) return@launch
            val missed = presenceTracker.onHeartbeat(beat)
            if (presenceUnsupported) return@launch
            try {
                withTimeoutOrNull(RobotCommandContract.PRESENCE_RELAY_TIMEOUT_MS) { authenticatedClient().postPresence(beat.toServerJson(missed)) }
            } catch (e: HydraApiException) {
                if (e.isNotFound) presenceUnsupported = true
                Log.w(TAG, "presence relay refused: HTTP ${e.httpCode}")
            } catch (e: Exception) {
                Log.w(TAG, "presence relay failed", e)
            }
        }
    }

    /**
     * The second tap of a spoken order. Only when the operator switched the relay on in the app, with a session; the server answers
     * with an assistant reply for the new request id, which goes back to the watch. Nothing else can come out of the watch.
     */
    private fun relayVoiceConfirm(event: MessageEvent) {
        val confirm = runCatching { WatchVoiceConfirm.fromJson(JSONObject(event.data.decodeToString())) }.getOrNull() ?: return
        requests.launch(confirm.requestId) {
            val on = ConnectionPrefs(applicationContext).loadFlag(ConnectionPrefs.Flags.VOICE_CONFIRM_RELAY, RobotCommandContract.VOICE_CONFIRM_ENABLED_BY_DEFAULT)
            val reply = if (!on) {
                WatchAssistantReply(confirm.requestId, getString(R.string.watch_confirm_relay_off), "ATTENTION", speak = false, requiresConfirmation = false)
            } else {
                runCatching {
                    withTimeoutOrNull(RobotCommandContract.VOICE_CONFIRM_TIMEOUT_MS) { authenticatedClient().postVoiceConfirm(confirm) }
                        ?: error("voice confirmation timed out")
                }.getOrElse { e ->
                    Log.w(TAG, "voice confirmation relay failed (${e.javaClass.simpleName})")
                    WatchAssistantReply(
                        requestId = confirm.requestId,
                        text = getString(R.string.watch_relay_connection_unavailable),
                        level = "ATTENTION",
                        speak = true,
                        requiresConfirmation = false,
                        errorCode = "connection_unavailable",
                    )
                }
            }
            send(event.sourceNodeId, WatchRelayPaths.ASSISTANT_REPLY, reply.toJson().toString())
        }
    }

    private suspend fun authenticatedClient(): HydraApiClient {
        val connection = ConnectionPrefs(applicationContext).load()
            ?: throw IllegalStateException("paired phone has no Server connection")
        val port = connection.second.toIntOrNull()?.takeIf { it in 1..65535 }
            ?: throw IllegalStateException("paired phone has an invalid Server port")
        val token = (sharedServicesOrNull()?.authStore ?: AuthPrefs(applicationContext)).loadAuth().token
            .takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("paired phone has no Server session")
        // The phone app may not be running: read the TLS choices of the user from storage.
        TlsSettingsStore.current = TlsPrefs(applicationContext).load()
        return HydraApiClient(connection.first, port).also { it.authToken = token }
    }

    private fun send(nodeId: String, path: String, payload: String) {
        Wearable.getMessageClient(this)
            .sendMessage(nodeId, path, payload.encodeToByteArray())
            .addOnFailureListener { e -> Log.w(TAG, "sendMessage($path) to $nodeId failed", e) }
    }
}
