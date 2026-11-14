// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - app/src/test/java/com/hydraumc/control/network/HydraWebSocketCanOtaTest.kt
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.network

import com.hydraumc.control.MainDispatcherSwap
import com.hydraumc.control.model.CanOtaPhase
import com.hydraumc.control.model.CanOtaProgress
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
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

/** A `canota_progress` message reaches its own callback and is never mistaken for a settings tree; a client that does not listen ignores it. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class HydraWebSocketCanOtaTest {

    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient
    private val sockets = mutableListOf<HydraWebSocket>()

    @Before
    fun setUp() {
        MainDispatcherSwap.set()
        server = MockWebServer()
        server.start()
        client = OkHttpClient()
    }

    @After
    fun tearDown() {
        try {
            server.shutdown()
        } finally {
            sockets.forEach { it.disconnect() }
            MainDispatcherSwap.reset()
        }
    }

    private val progressMessage =
        """{"type":"canota_progress","payload":{"phase":"transferring","pages_sent":5,"pages_total":20,"percent":25,"error":null}}"""

    private fun upgrade(vararg messages: String) = MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
            messages.forEach { webSocket.send(it) }
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(1000, null)
        }
    })

    @Test
    fun `a progress message goes to the progress callback and not to the settings one`() = runBlocking {
        val progress = Channel<JSONObject>(capacity = Channel.UNLIMITED)
        val settings = Channel<JSONObject>(capacity = Channel.UNLIMITED)
        server.enqueue(upgrade(progressMessage))
        val socket = HydraWebSocket(
            host = server.hostName,
            port = server.port,
            token = "tok",
            client = client,
            onStatus = {},
            onSettings = { settings.trySend(it) },
            onCanOtaProgress = { progress.trySend(it) },
            onError = {},
        ).also { sockets += it }
        socket.connect()

        val read = CanOtaProgress.fromMessage(withTimeout(5_000) { progress.receive() })!!
        assertEquals(CanOtaPhase.TRANSFERRING, read.phase)
        assertEquals(25, read.percent)
        assertTrue("a progress message is not a settings tree", settings.tryReceive().isFailure)
    }

    @Test
    fun `a client that does not listen for progress ignores the message`() = runBlocking {
        val settings = Channel<JSONObject>(capacity = Channel.UNLIMITED)
        server.enqueue(upgrade(progressMessage, """{"type":"settings","payload":{"controllers":[]}}"""))
        val socket = HydraWebSocket(
            host = server.hostName,
            port = server.port,
            token = "tok",
            client = client,
            onStatus = {},
            onSettings = { settings.trySend(it) },
            onError = {},
        ).also { sockets += it }
        socket.connect()

        val first = withTimeout(5_000) { settings.receive() }
        assertTrue("the settings message after it still arrives", first.has("controllers"))
        assertTrue(settings.tryReceive().isFailure)
    }
}
