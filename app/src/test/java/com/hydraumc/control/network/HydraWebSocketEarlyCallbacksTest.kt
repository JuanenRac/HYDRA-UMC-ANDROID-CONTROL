// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - app/src/test/java/com/hydraumc/control/network/HydraWebSocketEarlyCallbacksTest.kt
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.network

import com.hydraumc.control.MainDispatcherSwap
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * OkHttp calls the listener on its own thread, which may run before `newWebSocket` has returned to the caller (a server in the same
 * machine, a busy phone). Here a client that does exactly that, always, stands for it.
 */
@RunWith(RobolectricTestRunner::class)
class HydraWebSocketEarlyCallbacksTest {

    private class FakeSocket(private val opening: Request) : WebSocket {
        val closedWith = mutableListOf<Int>()
        override fun request() = opening
        override fun queueSize() = 0L
        override fun send(text: String) = true
        override fun send(bytes: ByteString) = true
        override fun close(code: Int, reason: String?): Boolean {
            closedWith += code
            return true
        }
        override fun cancel() {}
    }

    private fun upgrade(request: Request) =
        Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(101).message("Switching Protocols").build()

    @Before
    fun setUp() = MainDispatcherSwap.set()

    @After
    fun tearDown() = MainDispatcherSwap.reset()

    @Test
    fun callbacksThatArriveBeforeTheSocketIsReturnedAreNotLost() {
        val client = object : OkHttpClient() {
            override fun newWebSocket(request: Request, listener: WebSocketListener): WebSocket {
                val socket = FakeSocket(request)
                listener.onOpen(socket, upgrade(request))
                listener.onClosed(socket, 1008, "invalid token")
                return socket
            }
        }
        val statuses = mutableListOf<WsStatus>()
        val errors = mutableListOf<String>()
        val socket = HydraWebSocket(
            host = "127.0.0.1", port = 1, token = "an-expired-token", client = client,
            onStatus = { statuses += it }, onSettings = {}, onError = { errors += it },
        )

        socket.connect()

        assertEquals(listOf(WsStatus.CONNECTING, WsStatus.CONNECTED, WsStatus.DISCONNECTED), statuses)
        assertEquals(listOf(WS_UNAUTHORIZED_MESSAGE), errors)
        socket.disconnect()
    }

    @Test
    fun aSocketThatOpensAfterDisconnectIsClosedAndReportsNothing() {
        var listening: WebSocketListener? = null
        var opening: Request? = null
        var created: FakeSocket? = null
        val client = object : OkHttpClient() {
            override fun newWebSocket(request: Request, listener: WebSocketListener): WebSocket {
                listening = listener
                opening = request
                return FakeSocket(request).also { created = it }
            }
        }
        val statuses = mutableListOf<WsStatus>()
        val socket = HydraWebSocket(
            host = "127.0.0.1", port = 1, token = "tok", client = client,
            onStatus = { statuses += it }, onSettings = {}, onError = {},
        )
        socket.connect()
        socket.disconnect()
        val first = created!!
        first.closedWith.clear()

        listening!!.onOpen(first, upgrade(opening!!))

        assertEquals(listOf(WsStatus.CONNECTING), statuses)
        assertTrue(first.closedWith.contains(1000))
    }
}
