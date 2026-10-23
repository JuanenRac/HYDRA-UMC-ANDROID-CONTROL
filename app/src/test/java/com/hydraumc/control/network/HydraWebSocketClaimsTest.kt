// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - app/src/test/java/com/hydraumc/control/network/HydraWebSocketClaimsTest.kt
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The `claims` message of the server (who reserves which robot, pushed when a reservation, an arming or a stop changes)
 * reaches its own callback, carries the list as GET /api/claims does, and is never mistaken for a settings tree.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class HydraWebSocketClaimsTest {

    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        server = MockWebServer()
        server.start()
        client = OkHttpClient()
    }

    @After
    fun tearDown() {
        try {
            server.shutdown()
        } finally {
            Dispatchers.resetMain()
        }
    }

    private val claimsMessage =
        """{"type":"claims","schema":2,"claims":[{"robotId":3,"robotName":"A3","ownerUsername":"ana","controllerId":"android-1","expiresAt":9000,"jogArmedUntil":7000}]}"""

    @Test
    fun `a claims message goes to the claims callback and not to the settings one`() = runBlocking {
        val claims = Channel<JSONObject>(capacity = Channel.UNLIMITED)
        val settings = Channel<JSONObject>(capacity = Channel.UNLIMITED)
        val statuses = Channel<WsStatus>(capacity = Channel.UNLIMITED)
        server.enqueue(
            MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
                    webSocket.send(claimsMessage)
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    webSocket.close(1000, null)
                }
            }),
        )

        val socket = HydraWebSocket(
            host = server.hostName,
            port = server.port,
            token = "tok",
            client = client,
            onStatus = { statuses.trySend(it) },
            onSettings = { settings.trySend(it) },
            onClaims = { claims.trySend(it) },
            onError = {},
        )
        socket.connect()

        val message = withTimeout(5_000) { claims.receive() }
        val list = ClaimInfo.listFromJson(message)
        assertEquals(1, list.size)
        assertEquals(3, list[0].robotId)
        assertEquals("android-1", list[0].controllerId)
        assertEquals(7000L, list[0].jogArmedUntil)
        assertTrue("a claims message is not a settings tree", settings.tryReceive().isFailure)
        socket.disconnect()
    }

    @Test
    fun `a client that does not listen for claims ignores the message`() = runBlocking {
        val settings = Channel<JSONObject>(capacity = Channel.UNLIMITED)
        val statuses = Channel<WsStatus>(capacity = Channel.UNLIMITED)
        server.enqueue(
            MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
                    webSocket.send(claimsMessage)
                    webSocket.send("""{"type":"settings","payload":{"controllers":[]}}""")
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    webSocket.close(1000, null)
                }
            }),
        )

        val socket = HydraWebSocket(
            host = server.hostName,
            port = server.port,
            token = "tok",
            client = client,
            onStatus = { statuses.trySend(it) },
            onSettings = { settings.trySend(it) },
            onError = {},
        )
        socket.connect()

        val first = withTimeout(5_000) { settings.receive() }
        assertTrue("the settings message after it still arrives", first.has("controllers"))
        assertTrue(settings.tryReceive().isFailure)
        socket.disconnect()
    }
}
