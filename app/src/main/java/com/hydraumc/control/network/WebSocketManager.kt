package com.hydraumc.control.network

import okhttp3.*
import okio.ByteString

class WebSocketManager(private val ip: String, private val port: String) {
    private val client = OkHttpClient()
    private var webSocket: WebSocket? = null

    fun connect(listener: (String) -> Unit) {
        val request = Request.Builder()
            .url("ws://$ip:$port/ws")
            .build()
            
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                listener(text)
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                listener("{\"error\": \"Connection failed: ${t.message}\"}")
            }
        })
    }

    fun disconnect() {
        webSocket?.close(1000, "App closed")
    }
}
