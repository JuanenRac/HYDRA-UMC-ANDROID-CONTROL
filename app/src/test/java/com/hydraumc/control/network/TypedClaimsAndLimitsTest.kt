// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - app/src/test/java/com/hydraumc/control/network/TypedClaimsAndLimitsTest.kt
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.network

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
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
}
