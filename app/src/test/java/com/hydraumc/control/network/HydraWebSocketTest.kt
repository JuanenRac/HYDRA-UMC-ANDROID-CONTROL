// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - app/src/test/java/com/hydraumc/control/network/HydraWebSocketTest.kt
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0 - see LICENSE
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
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Found while auditing the code: HydraWebSocket's
 * own real WS_CLOSE_POLICY_VIOLATION (1008) handling - stop reconnecting
 * and surface a re-authentication error instead of spinning forever
 * against a token the server will never accept again - had real code but
 * zero test coverage anywhere in this app. Real, if local, WebSocket
 * server ([MockWebServer]'s own `withWebSocketUpgrade`), never a mocked
 * [WebSocket] - the server actually sends the exact close code server.ts
 * sends for a missing/invalid/expired token, and this test observes the
 * client's real reaction to it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HydraWebSocketTest {

    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient

    @Before
    fun setUp() {
        // HydraWebSocket dispatches every callback through Dispatchers.Main -
        // real reason in its own header comment (org.json isn't thread-safe
        // against the callers those callbacks mutate). A plain JVM unit test
        // has no real Android main looper to back that dispatcher.
        Dispatchers.setMain(UnconfinedTestDispatcher())
        server = MockWebServer()
        server.start()
        client = OkHttpClient()
    }

    @After
    fun tearDown() {
        server.shutdown()
        Dispatchers.resetMain()
    }

    /** Collects callback events onto a channel so the test can await them
     * in order without a fixed sleep guessing how long OkHttp needs. */
    private class RecordedEvents {
        val statuses = Channel<WsStatus>(capacity = Channel.UNLIMITED)
        val errors = Channel<String>(capacity = Channel.UNLIMITED)
    }

    @Test
    fun `onClosed with code 1008 surfaces a re-auth error and never reconnects`() = runBlocking {
        val events = RecordedEvents()
        server.enqueue(
            MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
                    // Real server.ts behavior for a missing/invalid/expired token:
                    // accept the HTTP upgrade, then immediately close with 1008 -
                    // never a plain HTTP 401 (the upgrade already succeeded).
                    webSocket.close(1008, "invalid token")
                }
            })
        )

        val socket = HydraWebSocket(
            host = server.hostName,
            port = server.port,
            token = "an-expired-token",
            client = client,
            onStatus = { events.statuses.trySend(it) },
            onSettings = {},
            onError = { events.errors.trySend(it) },
        )
        socket.connect()

        withTimeout(5_000) {
            assertEquals(WsStatus.CONNECTING, events.statuses.receive())
            assertEquals(WsStatus.CONNECTED, events.statuses.receive())
            assertEquals(WsStatus.DISCONNECTED, events.statuses.receive())
            val message = events.errors.receive()
            assertTrue(
                "expected a re-authentication message, got: $message",
                message.contains("no autorizada", ignoreCase = true),
            )
        }

        // Real proof of "never reconnects", not just an absence-of-log
        // assumption: wait comfortably past RECONNECT_DELAY_MS (3s) and
        // confirm the server never receives a second WebSocket upgrade
        // request - MockWebServer's own request queue would otherwise
        // report it via takeRequest().
        kotlinx.coroutines.delay(3_500)
        assertEquals(
            "a second CONNECTING status would mean it reconnected despite the 1008",
            0,
            server.requestCount - 1,
        )
    }

    @Test
    fun `onClosed with an ordinary code schedules a real reconnect`() = runBlocking {
        val events = RecordedEvents()
        // First connection: server closes with an ordinary code (not 1008) -
        // e.g. restarting/going away, never an auth rejection.
        server.enqueue(
            MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
                    webSocket.close(1001, "server restarting")
                }
            })
        )
        // Second connection: this repo's own real HydraWebSocket must retry
        // and this time reach a server that accepts and stays open.
        server.enqueue(
            MockResponse().withWebSocketUpgrade(object : WebSocketListener() {})
        )

        val socket = HydraWebSocket(
            host = server.hostName,
            port = server.port,
            token = "a-still-valid-token",
            client = client,
            onStatus = { events.statuses.trySend(it) },
            onSettings = {},
            onError = { events.errors.trySend(it) },
        )
        socket.connect()

        withTimeout(6_000) {
            assertEquals(WsStatus.CONNECTING, events.statuses.receive()) // 1st attempt
            assertEquals(WsStatus.CONNECTED, events.statuses.receive())
            assertEquals(WsStatus.DISCONNECTED, events.statuses.receive()) // server closed 1001
            assertEquals(WsStatus.CONNECTING, events.statuses.receive()) // real reconnect, after RECONNECT_DELAY_MS
            assertEquals(WsStatus.CONNECTED, events.statuses.receive())
        }
        assertEquals(2, server.requestCount)
    }
}
