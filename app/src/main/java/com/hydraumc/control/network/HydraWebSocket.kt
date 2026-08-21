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

/** RFC 6455 close code the server sends for a missing/invalid/expired auth token. */
private const val WS_CLOSE_POLICY_VIOLATION = 1008

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
    private val token: String? = null,
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
    // OkHttp's WebSocketListener callbacks (onOpen/onMessage/onClosing/
    // onClosed/onFailure) run on OkHttp's own internal dispatcher threads,
    // never Dispatchers.Main - but onSettings/onStatus/onError all flow into
    // RobotViewModel callbacks that mutate the same org.json JSONObject/
    // JSONArray tree sendAtomicCommand()'s localMutate touches from the main
    // thread. org.json isn't thread-safe, so without this dispatch the two
    // could race on the same live JSONObject. JSON parsing itself
    // (handleMessage's JSONObject(text) call) stays off this scope, running
    // on the caller's own (background) thread, since a full state payload
    // can be large enough that parsing it on Main would visibly jank the UI.
    private val mainScope = CoroutineScope(Dispatchers.Main.immediate)

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
        // Pass the token in the query string so the server can authenticate the upgrade
        val url = if (token != null) {
            "ws://$host:$port/ws?token=$token"
        } else {
            "ws://$host:$port/ws"
        }
        val request = Request.Builder().url(url).build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                mainScope.launch { onStatus(WsStatus.CONNECTED) }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleMessage(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                mainScope.launch { onStatus(WsStatus.DISCONNECTED) }
                if (code == WS_CLOSE_POLICY_VIOLATION) {
                    // server.ts closes the /ws upgrade with 1008 specifically when the
                    // token query param is missing/invalid/expired (see this class's own
                    // ?token= in openSocket()) - retrying the exact same token every
                    // RECONNECT_DELAY_MS forever just spins against a server that will
                    // never accept it, and silently masks a session the user actually
                    // needs to re-authenticate. Surface it instead and stop reconnecting;
                    // the ViewModel's own login() supplies a fresh token to try again.
                    closingByUser = true
                    mainScope.launch { onError("Sesión no autorizada: inicia sesión de nuevo") }
                    return
                }
                scheduleReconnect()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                mainScope.launch {
                    onStatus(WsStatus.DISCONNECTED)
                    onError("Conexión WebSocket perdida: ${t.message ?: t::class.simpleName}")
                }
                scheduleReconnect()
            }
        })
    }

    /** 
     * Processes an incoming raw WebSocket message. 
     * @param text The received message text.
     */
    private fun handleMessage(text: String) {
        val json = try {
            JSONObject(text)
        } catch (e: Exception) {
            mainScope.launch { onError("Mensaje WebSocket no es JSON válido: ${e.message}") }
            return
        }

        // Handle server-side error messages (e.g. Auth failure before close)
        val error = json.optString("error")
        if (error.isNotEmpty()) {
            mainScope.launch { onError(error) }
            return
        }

        val type = json.optString("type")
        if (type != "settings" && type != "delta") return

        val payload = json.optJSONObject("payload") ?: return

        // Simple idempotency guard. Semantic check is no longer needed since
        // the server doesn't broadcast the echo back to the sender.
        val payloadJson = payload.toString()
        if (payloadJson == lastPayloadJson) return

        lastPayloadJson = payloadJson
        // Dispatched to Main, not called directly from this (background)
        // thread - see mainScope's own comment above.
        mainScope.launch { onSettings(payload) }
    }

    /** 
     * Sends the current state to the server via WebSocket.
     * @param payload The state payload to send.
     * @return True if the message was sent, false if the socket is closed.
     */
    fun send(payload: JSONObject): Boolean {
        // Check the socket BEFORE the echo-guard, not after - a payload
        // that happens to match the last one sent/received returns "true"
        // (nothing to do) below regardless of whether the socket is
        // actually connected, so checking connectivity second would report
        // a fake success for exactly the case that matters most: the
        // reconnect window right after a drop.
        val socket = webSocket ?: return false
        val payloadJson = payload.toString()
        if (payloadJson == lastPayloadJson) return true
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
