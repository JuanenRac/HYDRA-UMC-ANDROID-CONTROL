// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - Wear OS voice relay service
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0 - see LICENSE
// =============================================================================
// Receives bounded recognised text/status requests from the paired Watch via
// Google Play services Data Layer. The package/signature boundary protects
// this channel; the Server JWT remains encrypted only on the phone.
package com.hydraumc.control.wear

import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import com.hydraumc.control.network.AuthPrefs
import com.hydraumc.control.network.ConnectionPrefs
import com.hydraumc.control.network.HydraApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONObject

object WatchRelayPaths {
    const val VOICE_TURN = "/hydra-umc/voice-turn/v1"
    const val STATUS_REQUEST = "/hydra-umc/system-status/v1"
    const val ASSISTANT_REPLY = "/hydra-umc/assistant-reply/v1"
    const val SYSTEM_STATUS = "/hydra-umc/system-status-reply/v1"
}

class WatchVoiceRelayService : WearableListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onMessageReceived(event: MessageEvent) {
        when (event.path) {
            WatchRelayPaths.VOICE_TURN -> relayVoiceTurn(event)
            WatchRelayPaths.STATUS_REQUEST -> relaySystemStatus(event)
            else -> Unit
        }
    }

    private fun relayVoiceTurn(event: MessageEvent) = scope.launch {
        val turn = runCatching { WatchVoiceTurn.fromJson(JSONObject(event.data.decodeToString())) }.getOrElse {
            return@launch
        }
        val reply = runCatching { authenticatedClient().postWatchVoiceTurn(turn) }.getOrElse {
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

    private fun relaySystemStatus(event: MessageEvent) = scope.launch {
        val status = runCatching { authenticatedClient().getWatchSystemStatus() }.getOrElse {
            WatchSystemStatus(
                headline = "HYDRA-UMC offline",
                detail = "Check the paired phone connection and Server session.",
                level = "OFFLINE",
                speak = false,
            )
        }
        send(event.sourceNodeId, WatchRelayPaths.SYSTEM_STATUS, status.toJson().toString())
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
    }
}
