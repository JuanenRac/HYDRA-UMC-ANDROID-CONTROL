// =============================================================================
// HYDRA-UMC CONTROL - WebSocket client for real-time state synchronization
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0 - see LICENSE
//
// /ws live-sync connection (REMOTE_API.md section 3). On connect, the server
// immediately sends one {"type":"settings","payload":{...}} message with the
// current full state, then pushes the same shape to every connected client
// (sender included) whenever the state changes, from either a POST
// /api/settings from anyone or a client sending the same envelope back over
// this same socket.
//
// Replaces the old WebSocketManager, which only overrode onFailure() and
// forwarded its message as if it were just another WS text frame - since
// that JSON ({"error": "..."}) parses fine, the ViewModel's own try/catch
// around JSONObject parsing never actually caught it, so a connection
// failure left the UI stuck on "Conectando..." forever. This class instead
// reports connection lifecycle (open/closing/closed/failure) through
// dedicated callbacks - never as a fake WS message - and reconnects on an
// unexpected drop with a fixed delay, mirroring HYDRA-UMC SUITE's own
// hydra_suite/net/client.py HydraConnection._run() (RECONNECT_DELAY_S = 3s),
// so a HYDRA-UMC on a flaky Wi-Fi link doesn't require the user to manually
// reconnect. Also carries the same echo guard as client.py/src/store.tsx's
// own lastPayloadJsonRef: the server broadcasts every write back to the
// sender too, so without this a local edit would re-trigger itself as if it
// were a fresh external change.
// =============================================================================
package com.hydraumc.control.network

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject

/** Delay before attempting to reconnect a dropped WebSocket. */
private const val RECONNECT_DELAY_MS = 3_000L

/** 
 * Enumeration of possible WebSocket connection statuses. 
 */
enum class WsStatus { 
    /** The socket is attempting to establish a connection. */
    CONNECTING, 
    /** The socket is connected and ready for communication. */
    CONNECTED, 
    /** The socket is currently disconnected. */
    DISCONNECTED 
}

/**
 * Manages the WebSocket connection for live bidirectional sync.
 * @property host The server host.
 * @property port The server port.
 * @property client The shared OkHttpClient instance.
 * @property onStatus Callback for connection status updates.
 * @property onSettings Callback for received settings payloads.
 * @property onError Callback for error messages.
 */
class HydraWebSocket(
    private val host: String,
    private val port: Int,
    private val client: OkHttpClient = HydraApiClient.sharedHttpClient,
    private val onStatus: (WsStatus) -> Unit,
    private val onSettings: (JSONObject) -> Unit,
    private val onError: (String) -> Unit,
) {
    /** The underlying OkHttp WebSocket instance. */
    private var webSocket: WebSocket? = null
    /** Flag to prevent auto-reconnect when the user manually disconnects. */
    private var closingByUser = false
    /** Active coroutine job handling reconnection delays. */
    private var reconnectJob: Job? = null
    /** Coroutine scope for management tasks. */
    private val scope = CoroutineScope(Dispatchers.IO)

    /** Rationale: The server broadcasts every write back to the sender too. */
    private var lastPayloadJson: String? = null

    /** 
     * Initiates the WebSocket connection. 
     */
    fun connect() {
        closingByUser = false
        openSocket()
    }

    /** 
     * Opens a new WebSocket to the server. 
     */
    private fun openSocket() {
        onStatus(WsStatus.CONNECTING)
        val request = Request.Builder().url("ws://$host:$port/ws").build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                onStatus(WsStatus.CONNECTED)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleMessage(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                onStatus(WsStatus.DISCONNECTED)
                scheduleReconnect()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                onStatus(WsStatus.DISCONNECTED)
                onError("Conexión WebSocket perdida: ${t.message ?: t::class.simpleName}")
                scheduleReconnect()
            }
        })
    }

    /** 
     * Processes an incoming raw WebSocket message. 
     * @param text The received message text.
     */
    private fun handleMessage(text: String) {
        /** Parsed JSON envelope. */
        val json = try {
            JSONObject(text)
        } catch (e: Exception) {
            onError("Mensaje WebSocket no es JSON válido: ${e.message}")
            return
        }
        if (json.optString("type") != "settings") return 
        /** The settings payload within the envelope. */
        val payload = json.optJSONObject("payload") ?: return
        val payloadJson = payload.toString()
        if (payloadJson == lastPayloadJson) return // our own echoed-back write
        lastPayloadJson = payloadJson
        onSettings(payload)
    }

    /** 
     * Sends the current state to the server via WebSocket.
     * @param payload The state payload to send.
     * @return True if the message was sent, false if the socket is closed.
     */
    fun send(payload: JSONObject): Boolean {
        val payloadJson = payload.toString()
        if (payloadJson == lastPayloadJson) return true 
        val socket = webSocket ?: return false
        /** Envelope following the REMOTE_API.md contract. */
        val envelope = JSONObject().put("type", "settings").put("payload", payload)
        val sent = socket.send(envelope.toString())
        if (sent) lastPayloadJson = payloadJson
        return sent
    }

    /** 
     * Schedules a reconnection attempt after a delay. 
     */
    private fun scheduleReconnect() {
        if (closingByUser) return
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            delay(RECONNECT_DELAY_MS)
            if (!closingByUser) openSocket()
        }
    }

    /** 
     * Disconnects the WebSocket and stops auto-reconnection. 
     */
    fun disconnect() {
        closingByUser = true
        reconnectJob?.cancel()
        webSocket?.close(1000, "App closed")
        webSocket = null
    }
}
