// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - Wear OS voice relay service
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0 - see LICENSE
// =============================================================================
// Receives bounded recognised text/status requests from the paired Watch via
// Google Play services Data Layer. The package/signature boundary protects
// this channel; the Server JWT remains encrypted only on the phone.
//
// Found in an ecosystem-wide software-improvements audit (ANDROID-01): this
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
import com.hydraumc.control.network.AuthPrefs
import com.hydraumc.control.network.ConnectionPrefs
import com.hydraumc.control.network.HydraApiClient
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
}

private const val TAG = "WatchVoiceRelay"

/** How long a single relayed request may run before it's treated as failed -
 * a hung phone/Server round trip must not hold a slot (or, before the
 * ANDROID-01 fix, the whole service) open indefinitely. */
private const val REQUEST_TIMEOUT_MS = 15_000L

class WatchVoiceRelayService : WearableListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val requests = BoundedRequestScope(scope)

    override fun onDestroy() {
        // The real ANDROID-01 fix: previously nothing here at all, so every
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
                WatchAssistantReply(
                    requestId = turn.requestId,
                    text = "HYDRA-UMC connection unavailable. Check the paired phone session.",
                    level = "ATTENTION",
                    speak = true,
                    requiresConfirmation = false,
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
                withTimeoutOrNull(REQUEST_TIMEOUT_MS) { authenticatedClient().getWatchSystemStatus() }
                    ?: error("system status request timed out after ${REQUEST_TIMEOUT_MS}ms")
            }.getOrElse {
                WatchSystemStatus(
                    headline = "HYDRA-UMC offline",
                    detail = "Check the paired phone connection and Server session.",
                    level = "OFFLINE",
                    speak = false,
                )
            }
            send(event.sourceNodeId, WatchRelayPaths.SYSTEM_STATUS, status.toJson().toString())
        }
    }

    private suspend fun authenticatedClient(): HydraApiClient {
        val connection = ConnectionPrefs(applicationContext).load()
            ?: throw IllegalStateException("paired phone has no Server connection")
        val port = connection.second.toIntOrNull()?.takeIf { it in 1..65535 }
            ?: throw IllegalStateException("paired phone has an invalid Server port")
        val token = AuthPrefs(applicationContext).loadAuth().token
            .takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("paired phone has no Server session")
        return HydraApiClient(connection.first, port).also { it.authToken = token }
    }

    private fun send(nodeId: String, path: String, payload: String) {
        Wearable.getMessageClient(this)
            .sendMessage(nodeId, path, payload.encodeToByteArray())
            .addOnFailureListener { e -> Log.w(TAG, "sendMessage($path) to $nodeId failed", e) }
    }
}
