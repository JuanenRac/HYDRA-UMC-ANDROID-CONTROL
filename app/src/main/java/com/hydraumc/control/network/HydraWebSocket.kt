// =============================================================================
// HYDRA-UMC Android Control - network/HydraWebSocket.kt
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

private const val RECONNECT_DELAY_MS = 3_000L

enum class WsStatus { CONNECTING, CONNECTED, DISCONNECTED }

class HydraWebSocket(
    private val host: String,
    private val port: Int,
    private val client: OkHttpClient = HydraApiClient.sharedHttpClient,
    private val onStatus: (WsStatus) -> Unit,
    private val onSettings: (JSONObject) -> Unit,
    private val onError: (String) -> Unit,
) {
    private var webSocket: WebSocket? = null
    private var closingByUser = false
    private var reconnectJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    // Mirrors client.py's own _last_payload_json guard (see this file's header).
    private var lastPayloadJson: String? = null

    fun connect() {
        closingByUser = false
        openSocket()
    }

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

    private fun handleMessage(text: String) {
        val json = try {
            JSONObject(text)
        } catch (e: Exception) {
            onError("Mensaje WebSocket no es JSON válido: ${e.message}")
            return
        }
        if (json.optString("type") != "settings") return // only "type" this app knows about (REMOTE_API.md section 3)
        val payload = json.optJSONObject("payload") ?: return
        val payloadJson = payload.toString()
        if (payloadJson == lastPayloadJson) return // our own echoed-back write
        lastPayloadJson = payloadJson
        onSettings(payload)
    }

    /** Sends the full state over the open socket - functionally identical to
     * POST /api/settings, offered by the server as a convenience so a client
     * that already holds the socket open doesn't need a second HTTP round
     * trip (REMOTE_API.md section 3). Returns false if the socket isn't open,
     * so the caller can fall back to a REST POST (mirrors client.py's
     * push_state()). */
    fun send(payload: JSONObject): Boolean {
        val payloadJson = payload.toString()
        if (payloadJson == lastPayloadJson) return true // unchanged since our own last send/receive
        val socket = webSocket ?: return false
        val envelope = JSONObject().put("type", "settings").put("payload", payload)
        val sent = socket.send(envelope.toString())
        if (sent) lastPayloadJson = payloadJson
        return sent
    }

    private fun scheduleReconnect() {
        if (closingByUser) return
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            delay(RECONNECT_DELAY_MS)
            if (!closingByUser) openSocket()
        }
    }

    fun disconnect() {
        closingByUser = true
        reconnectJob?.cancel()
        webSocket?.close(1000, "App closed")
        webSocket = null
    }
}
