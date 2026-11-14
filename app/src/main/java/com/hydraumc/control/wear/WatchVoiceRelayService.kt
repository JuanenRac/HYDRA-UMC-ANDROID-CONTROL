// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - Wear OS voice relay service
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
// Receives bounded recognised text/status requests from the paired Watch via
// Google Play services Data Layer. The package/signature boundary protects
// this channel; the Server JWT remains encrypted only on the phone.
//
// The service only wires Android to WatchRelayHandler (the five routes) and
// owns the scope: onDestroy() cancels every request still in flight, and each
// request has its own timeout (see BoundedRequestScope.kt).
package com.hydraumc.control.wear

import android.util.Log
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import com.hydraumc.control.R
import com.hydraumc.control.network.AuthPrefs
import com.hydraumc.control.sharedServicesOrNull
import com.hydraumc.control.network.ConnectionPrefs
import com.hydraumc.control.network.HydraApiClient
import com.hydraumc.control.network.RobotCommandContract
import com.hydraumc.control.network.TlsPrefs
import com.hydraumc.control.network.TlsSettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

object WatchRelayPaths {
    const val VOICE_TURN = "/hydra-umc/voice-turn/v1"
    const val STATUS_REQUEST = "/hydra-umc/system-status/v1"
    const val ASSISTANT_REPLY = "/hydra-umc/assistant-reply/v1"
    const val SYSTEM_STATUS = "/hydra-umc/system-status-reply/v1"
    /** Watch -> phone: asks for the read-only security status of the server. */
    const val SERVER_SECURITY_REQUEST = "/hydra-umc/server-security-request/v1"
    /** Phone -> watch: the answer. */
    const val SERVER_SECURITY = "/hydra-umc/server-security/v1"
    /** Phone -> watch: the live progress of a flash cycle of a board. */
    const val CANOTA_PROGRESS = "/hydra-umc/canota-progress/v1"
}

private const val TAG = "WatchVoiceRelay"

class WatchVoiceRelayService : WearableListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val requests = BoundedRequestScope(scope)

    private val handler: WatchRelayHandler by lazy {
        WatchRelayHandler(
            requests = requests,
            backend = ClientRelayBackend { authenticatedClient() },
            sender = MessageSender { nodeId, path, payload ->
                Wearable.getMessageClient(this)
                    .sendMessage(nodeId, path, payload)
                    .addOnFailureListener { e -> Log.w(TAG, "sendMessage($path) to $nodeId failed", e) }
            },
            flags = object : WatchRelayFlags {
                override suspend fun presenceRelay() =
                    ConnectionPrefs(applicationContext).loadFlag(ConnectionPrefs.Flags.PRESENCE_RELAY, RobotCommandContract.PRESENCE_RELAY_ENABLED_BY_DEFAULT)

                override suspend fun voiceConfirmRelay() =
                    ConnectionPrefs(applicationContext).loadFlag(ConnectionPrefs.Flags.VOICE_CONFIRM_RELAY, RobotCommandContract.VOICE_CONFIRM_ENABLED_BY_DEFAULT)
            },
            texts = WatchRelayTexts(
                connectionUnavailable = getString(R.string.watch_relay_connection_unavailable),
                offlineHeadline = getString(R.string.watch_status_offline_headline),
                offlineDetail = getString(R.string.watch_status_offline_detail),
                confirmRelayOff = getString(R.string.watch_confirm_relay_off),
            ),
            onLog = { Log.w(TAG, it) },
        )
    }

    override fun onDestroy() {
        requests.cancelAll()
        scope.cancel()
        super.onDestroy()
    }

    override fun onMessageReceived(event: MessageEvent) {
        handler.onMessage(event.path, event.sourceNodeId, event.data)
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
}
