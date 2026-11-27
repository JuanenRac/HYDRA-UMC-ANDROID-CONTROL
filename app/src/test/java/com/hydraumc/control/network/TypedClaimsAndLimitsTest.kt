// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - app/src/test/java/com/hydraumc/control/network/TypedClaimsAndLimitsTest.kt
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The reservations and the safety limits go through the typed client of the SDK: same requests, same errors. */
@RunWith(RobolectricTestRunner::class)
class TypedClaimsAndLimitsTest {

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

    @Test
    fun `the claims are read from the typed client with the token`() {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"claims":[{"robotId":2,"ownerUsername":"ana","claimedAt":1000,"expiresAt":2000}]}""",
            ),
        )
        val claims = runBlocking { client.getClaims() }
        assertEquals(1, claims.size)
        val request = server.takeRequest()
        assertEquals("/api/claims", request.path)
        assertEquals("Bearer tok.en", request.getHeader("Authorization"))
    }

    @Test
    fun `a claim list that misses a required field is an API error, not a crash`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"claims":[{"robotId":2}]}"""))
        try {
            runBlocking { client.getClaims() }
            fail("expected a HydraApiException")
        } catch (e: HydraApiException) {
            assertNull(e.httpCode)
        }
    }

    @Test
    fun `the safety limits of an older server without the route are null`() {
        server.enqueue(MockResponse().setResponseCode(404).setBody("""{"error":"not found"}"""))
        assertNull(runBlocking { client.getRobotSafety(3) })
        assertEquals("/api/safety-limits?robotId=3", server.takeRequest().path)
    }

    @Test
    fun `another error of the safety limits is thrown`() {
        server.enqueue(MockResponse().setResponseCode(500).setBody("""{"error":"boom"}"""))
        try {
            runBlocking { client.getRobotSafety(3) }
            fail("expected a HydraApiException")
        } catch (e: HydraApiException) {
            assertTrue(e.httpCode == 500)
        }
    }

    @Test
    fun `a claim goes out with the token, the controller and the revision, and gives the new revision`() {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"success":true,"reservation":{"ownerUsername":"ana","expiresAt":2000},"reservationHistory":[],"revision":"r2"}""",
            ),
        )
        val answer = runBlocking { client.postClaim(2, ifMatch = "\"r1\"") }
        assertEquals("r2", answer.getString("revision"))
        assertEquals("ana", answer.getJSONObject("reservation").getString("ownerUsername"))
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/robot/2/claim", request.path)
        assertEquals("Bearer tok.en", request.getHeader("Authorization"))
        assertEquals("\"r1\"", request.getHeader("If-Match"))
        assertEquals(SessionController.id, request.getHeader("X-Controller-Id"))
        assertEquals("""{"controllerId":"${SessionController.id}"}""", request.body.readUtf8())
    }

    @Test
    fun `a take over sends force and the life of the claim`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"success":true,"reservation":{},"reservationHistory":[],"revision":"r3"}"""))
        runBlocking { client.postClaim(2, ttlMs = 60_000L, force = true) }
        val request = server.takeRequest()
        val body = JSONObject(request.body.readUtf8())
        assertTrue(body.getBoolean("force"))
        assertEquals(60_000L, body.getLong("ttlMs"))
        assertNull(request.getHeader("If-Match"))
    }

    @Test
    fun `an older server with no revision and a release with no history are accepted`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"success":true,"reservation":{},"reservationHistory":[]}"""))
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"success":true}"""))
        assertNull(revisionOf(runBlocking { client.postClaim(2) }))
        assertTrue(runBlocking { client.postRelease(2) }.getBoolean("success"))
        assertEquals("/api/robot/2/claim", server.takeRequest().path)
        assertEquals("/api/robot/2/release", server.takeRequest().path)
    }

    @Test
    fun `a claim answer without a reservation is an API error`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"success":true}"""))
        try {
            runBlocking { client.postClaim(2) }
            fail("expected a HydraApiException")
        } catch (e: HydraApiException) {
            assertNull(e.httpCode)
        }
    }
}
