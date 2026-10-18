// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - app/src/test/java/com/hydraumc/control/network/HydraApiClientErrorsTest.kt
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
 * What the client keeps of the server's refusals: the HTTP status, the stable
 * `code` and the `error` text, so the app can tell an expired session from a
 * missing permission, an unknown command (400) and a robot reserved by
 * another account (409). Also the refresh and logout calls and the state
 * cache's removal of camera credentials.
 */
@RunWith(RobolectricTestRunner::class)
class HydraApiClientErrorsTest {

    private lateinit var server: MockWebServer
    private lateinit var client: HydraApiClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = HydraApiClient("127.0.0.1", server.port)
        client.authToken = "tok"
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun refusal(status: Int, body: String): HydraApiException {
        server.enqueue(MockResponse().setResponseCode(status).setBody(body))
        try {
            runBlocking { client.postRobotCommand(1, JSONObject().put("command", "x")) }
        } catch (e: HydraApiException) {
            return e
        }
        fail("expected a HydraApiException")
        throw IllegalStateException()
    }

    @Test
    fun `an expired token is a session problem the app can renew`() {
        val e = refusal(403, """{"error":"Access denied: token expired","code":"TOKEN_EXPIRED"}""")
        assertEquals(403, e.httpCode)
        assertEquals("TOKEN_EXPIRED", e.serverCode)
        assertTrue(e.isSessionProblem)
        assertFalse(e.isPermissionDenied)
    }

    @Test
    fun `a 403 without a token code is a missing permission, not an expired session`() {
        val e = refusal(403, """{"error":"Access denied: admin privileges required"}""")
        assertFalse(e.isSessionProblem)
        assertTrue(e.isPermissionDenied)
        assertEquals("Access denied: admin privileges required", e.serverMessage)
    }

    @Test
    fun `a 409 is a robot reserved by another account and keeps the reason`() {
        val e = refusal(409, """{"error":"robot 1 is reserved by alice"}""")
        assertTrue(e.isReservedByAnother)
        assertEquals("robot 1 is reserved by alice", e.serverMessage)
    }

    @Test
    fun `a 400 is a command the server refused`() {
        val e = refusal(400, """{"error":"unknown command"}""")
        assertTrue(e.isRejectedCommand)
        assertEquals("unknown command", e.serverMessage)
    }

    @Test
    fun `an answer that is not JSON still gives the status`() {
        val e = refusal(502, "<html>bad gateway</html>")
        assertEquals(502, e.httpCode)
        assertNull(e.serverCode)
        assertNull(e.serverMessage)
    }

    @Test
    fun `a network failure has no status`() {
        server.shutdown()
        try {
            runBlocking { client.postRobotCommand(1, JSONObject().put("command", "x")) }
            fail("expected a HydraApiException")
        } catch (e: HydraApiException) {
            assertNull(e.httpCode)
            assertFalse(e.isSessionProblem)
        }
        // tearDown shuts the server down again; that is harmless.
    }

    @Test
    fun `refresh posts the refresh token and returns the new pair`() {
        server.enqueue(MockResponse().setBody("""{"success":true,"token":"new","refreshToken":"r2","role":"admin"}"""))
        val answer = runBlocking { client.refresh("r1") }
        assertEquals("new", answer.getString("token"))
        val request = server.takeRequest()
        assertEquals("/api/refresh", request.path)
        assertEquals("r1", JSONObject(request.body.readUtf8()).getString("refreshToken"))
    }

    @Test
    fun `logout revokes the refresh token and never throws`() {
        server.enqueue(MockResponse().setResponseCode(500))
        runBlocking { client.logout("r1") }
        assertEquals("/api/logout", server.takeRequest().path)
    }

    @Test
    fun `the camera ticket is read from the server, or null on an older one`() {
        server.enqueue(MockResponse().setBody("""{"ticket":"abc","expiresInSeconds":300}"""))
        assertEquals("abc", runBlocking { client.fetchCameraTicket() })
        server.enqueue(MockResponse().setResponseCode(404))
        assertNull(runBlocking { client.fetchCameraTicket() })
    }

    @Test
    fun `the state cache drops camera credentials at any depth`() {
        val raw = """{"controllers":[{"cameras":[{"id":1,"ipUsername":"admin","ipPassword":"secret","host":"10.0.0.5"}]}],"ipPassword":"top"}"""
        val clean = JSONObject(StateCache.redactSecrets(raw))
        val camera = clean.getJSONArray("controllers").getJSONObject(0).getJSONArray("cameras").getJSONObject(0)
        assertFalse(camera.has("ipUsername"))
        assertFalse(camera.has("ipPassword"))
        assertFalse(clean.has("ipPassword"))
        assertEquals("10.0.0.5", camera.getString("host"))
        assertNotNull(clean.getJSONArray("controllers"))
    }
}
