// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - app/src/test/java/com/hydraumc/control/network/HydraApiClientAnswersTest.kt
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.network

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * What the client does with answers that are wrong, partial or hostile, and
 * what it sends: the token only in the Authorization header, the identity of
 * the app on every call, and nothing for a call that needs no credentials.
 */
@RunWith(RobolectricTestRunner::class)
class HydraApiClientAnswersTest {

    private lateinit var server: MockWebServer
    private lateinit var client: HydraApiClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = HydraApiClient("127.0.0.1", server.port)
        client.authToken = "tok.en"
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun failure(block: suspend () -> Any?): HydraApiException {
        try {
            runBlocking { block() }
        } catch (e: HydraApiException) {
            return e
        }
        fail("expected a HydraApiException")
        throw IllegalStateException()
    }

    @Test
    fun `a 200 whose body is not a JSON object is an error, never a crash`() {
        for (body in listOf("[]", "null", "5", "\"text\"", "<html>proxy</html>", "{", "[{}]")) {
            server.enqueue(MockResponse().setResponseCode(200).setBody(body))
            val error = failure { client.getSettings() }
            assertNull("body $body", error.httpCode)
        }
    }

    @Test
    fun `a 200 with an empty body is an error`() {
        server.enqueue(MockResponse().setResponseCode(200))
        failure { client.getSettings() }
    }

    @Test
    fun `a refusal keeps the status, the code and the text when they are text`() {
        server.enqueue(MockResponse().setResponseCode(403).setBody("""{"code":"TOKEN_EXPIRED","error":"Token expired"}"""))
        val error = failure { client.getSettings() }
        assertEquals(403, error.httpCode)
        assertEquals("TOKEN_EXPIRED", error.serverCode)
        assertEquals("Token expired", error.serverMessage)
        assertTrue(error.isSessionProblem)
    }

    @Test
    fun `a refusal whose body is not an object still gives the status and no code`() {
        for (body in listOf("", "[]", "null", "<html>bad gateway</html>", "\"x\"")) {
            server.enqueue(MockResponse().setResponseCode(502).setBody(body))
            val error = failure { client.getSettings() }
            assertEquals("body $body", 502, error.httpCode)
            assertNull("body $body", error.serverCode)
            assertNull("body $body", error.serverMessage)
        }
    }

    @Test
    fun `a refusal with a null or empty code does not invent one`() {
        server.enqueue(MockResponse().setResponseCode(400).setBody("""{"code":null,"error":""}"""))
        val error = failure { client.getSettings() }
        assertNull(error.serverCode)
        assertNull(error.serverMessage)
        assertTrue(error.isRejectedCommand)
    }

    @Test
    fun `the token goes in the Authorization header only, with the identity of the app`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        runBlocking {
            client.getSettings()
            client.postRobotCommand(7, JSONObject().put("command", "play"))
        }
        val settings = server.takeRequest()
        val command = server.takeRequest()
        assertEquals("/api/settings", settings.path)
        assertEquals("/api/robot/7/command", command.path)
        for (request in listOf(settings, command)) {
            assertEquals("Bearer tok.en", request.getHeader("Authorization"))
            assertEquals("android/${com.hydraumc.control.BuildConfig.VERSION_NAME}", request.getHeader("X-Hydra-Client"))
            assertFalse(request.path.orEmpty().contains("tok.en"))
        }
        assertEquals("play", JSONObject(command.body.readUtf8()).getString("command"))
    }

    @Test
    fun `login sends no token, and returns what the server answered`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"success":true,"token":"abc","refreshToken":"r1","role":"admin"}"""))
        val answer = runBlocking { client.login("alice", "secret") }
        val request = server.takeRequest()
        assertEquals("/api/login", request.path)
        assertNull(request.getHeader("Authorization"))
        assertEquals("android/${com.hydraumc.control.BuildConfig.VERSION_NAME}", request.getHeader("X-Hydra-Client"))
        val body = JSONObject(request.body.readUtf8())
        assertEquals("alice", body.getString("username"))
        assertEquals("secret", body.getString("password"))
        assertEquals("abc", answer.getString("token"))
        assertEquals("admin", answer.getString("role"))
    }

    @Test
    fun `a rejected login is a 401 the app can tell from a network failure`() {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":"Invalid credentials"}"""))
        val error = failure { client.login("alice", "wrong") }
        assertEquals(401, error.httpCode)
        assertEquals("Invalid credentials", error.serverMessage)
        assertTrue(error.isSessionProblem)
    }

    @Test
    fun `a rate limited login is 429`() {
        server.enqueue(MockResponse().setResponseCode(429).setBody("""{"error":"Too many login attempts"}"""))
        val error = failure { client.login("alice", "x") }
        assertTrue(error.isRateLimited)
        assertFalse(error.isSessionProblem)
    }

    @Test
    fun `a server that is not listening is an error without a status`() {
        val port = server.port
        server.shutdown()
        val dead = HydraApiClient("127.0.0.1", port)
        val error = failure { dead.getSettings() }
        assertNull(error.httpCode)
        assertNotNull(error.message)
        server = MockWebServer().also { it.start() } // the tear-down shuts a server down
    }

    @Test
    fun `hydra-info is a server only when it names a remote API version`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"product":"HYDRA-UMC-SERVER","remoteApiVersion":2}"""))
        assertNotNull(runBlocking { client.getHydraInfo() })

        for (body in listOf("""{"product":"other"}""", "[]", "null", "<html>", "")) {
            server.enqueue(MockResponse().setResponseCode(200).setBody(body))
            assertNull("body $body", runBlocking { HydraApiClient("127.0.0.1", server.port).getHydraInfo() })
        }
    }

    @Test
    fun `the global STOP posts without a body to stop-all and reads the answer`() {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"success":true,"stoppedCount":1,"failedCount":0,"persisted":true,"robots":[{"robotId":1,"stopped":true}]}""",
            ),
        )
        val result = runBlocking { client.postStopAll() }
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/stop-all", request.path)
        assertEquals("Bearer tok.en", request.getHeader("Authorization"))
        assertEquals(0L, request.bodySize)
        assertTrue(result.isClean)
    }

    @Test
    fun `the global STOP reports 404 and 429 as flags the caller turns into the per-robot stop`() {
        server.enqueue(MockResponse().setResponseCode(404).setBody("""{"error":"Cannot POST /api/stop-all"}"""))
        assertTrue(failure { client.postStopAll() }.isNotFound)
        server.enqueue(MockResponse().setResponseCode(429).setBody("""{"error":"slow down"}"""))
        assertTrue(failure { client.postStopAll() }.isRateLimited)
    }

    @Test
    fun `the global STOP does not wait for a slow server beyond its short timeout`() {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody("{}").setBodyDelay(5, java.util.concurrent.TimeUnit.SECONDS),
        )
        val started = System.currentTimeMillis()
        val error = failure { client.postStopAll() }
        assertNull(error.httpCode)
        assertTrue("took ${System.currentTimeMillis() - started} ms", System.currentTimeMillis() - started < 4_500)
    }
}
