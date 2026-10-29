package com.hydraumc.control.network

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The number on the wire: motion commands carry a rising `seq`, other commands none, and a 409 stale-sequence is its own refusal. */
@RunWith(RobolectricTestRunner::class)
class CommandSequenceWireTest {
    private lateinit var server: MockWebServer
    private lateinit var client: HydraApiClient

    @Before
    fun setUp() {
        CommandSequence.reset()
        server = MockWebServer()
        server.start()
        client = HydraApiClient("127.0.0.1", server.port)
        client.authToken = "tok"
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `a jog then a stop carry rising numbers and a home carries none`() {
        repeat(3) { server.enqueue(MockResponse().setResponseCode(200).setBody("{}")) }
        runBlocking {
            client.postRobotCommand(1, JSONObject().put("command", "jog"))
            client.postRobotCommand(1, JSONObject().put("command", "stop"), urgent = true)
            client.postRobotCommand(1, JSONObject().put("command", "home"))
        }
        val jog = JSONObject(server.takeRequest().body.readUtf8())
        val stop = JSONObject(server.takeRequest().body.readUtf8())
        val home = JSONObject(server.takeRequest().body.readUtf8())
        assertTrue(stop.getLong("seq") > jog.getLong("seq"))
        assertFalse(home.has("seq"))
    }

    @Test
    fun `a 409 stale-sequence is not taken for a reservation`() {
        server.enqueue(MockResponse().setResponseCode(409).setBody("""{"error":"stale","code":"stale-sequence","lastSeq":9}"""))
        try {
            runBlocking { client.postRobotCommand(1, JSONObject().put("command", "jog")) }
            fail("expected a HydraApiException")
        } catch (e: HydraApiException) {
            assertTrue(e.isStaleSequence)
            assertEquals(409, e.httpCode)
        }
    }
}
