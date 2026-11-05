// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - app/src/test/java/com/hydraumc/control/network/ServerAnswersTest.kt
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The answers of the server as the SDK generates them from its inventory (the vendored `server-answers.json`, kept
 * identical by tools/sync_sdk_answers.py): the minimal body (required fields only) and the full body (every declared
 * field) of each route the app reads must give a sane model through the real client, and a refusal body must reach the
 * app as an error with its status.
 */
@RunWith(RobolectricTestRunner::class)
class ServerAnswersTest {

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

    private fun routes(): JSONObject {
        val stream = javaClass.classLoader!!.getResourceAsStream("server-answers.json")
        assertNotNull("server-answers.json is missing from the test resources", stream)
        return JSONObject(stream!!.bufferedReader().use { it.readText() }).getJSONObject("routes")
    }

    private fun answer(route: String, status: String, kind: String): JSONObject =
        routes().getJSONObject(route).getJSONObject(status).getJSONObject(kind)

    private fun serve(status: Int, body: JSONObject) {
        server.enqueue(MockResponse().setResponseCode(status).setBody(body.toString()))
    }

    @Test
    fun `the inventory lists the routes the SDK knows and each answer has a minimal and a full body`() {
        val all = routes()
        assertTrue("expected the full route inventory, found ${all.length()}", all.length() >= 90)
        for (route in all.keys()) {
            val statuses = all.getJSONObject(route)
            for (status in statuses.keys()) {
                val kinds = statuses.getJSONObject(status)
                assertTrue("$route $status has no minimal body", kinds.optJSONObject("minimal") != null)
                assertTrue("$route $status has no full body", kinds.optJSONObject("full") != null)
            }
        }
    }

    @Test
    fun `every route the app calls is in the inventory`() {
        val inventory = routes().keys().asSequence().map { it.substringAfter(' ').replace(Regex("\{[^}]+\}"), "{p}") }.toSet()
        // The full list of the app lives in docs/ and is checked against the contract by tools/check_rest_routes.py;
        // here only the routes this test replays must be in the inventory.
        for (route in listOf("/api/hydra-info", "/api/stop-all", "/api/ecosystem/status", "/api/login", "/api/refresh", "/api/system/metrics")) {
            assertTrue("$route is not in the inventory", route in inventory)
        }
    }

    @Test
    fun `hydra-info of either kind identifies a server`() {
        for (kind in listOf("minimal", "full")) {
            serve(200, answer("GET /api/hydra-info", "200", kind))
            val info = runBlocking { client.getHydraInfo() }
            assertNotNull("kind $kind", info)
            assertTrue(info!!.has("remoteApiVersion"))
        }
    }

    @Test
    fun `stop-all of either kind reads as a result whose counts are whole numbers`() {
        for (kind in listOf("minimal", "full")) {
            serve(200, answer("POST /api/stop-all", "200", kind))
            val result = runBlocking { client.postStopAll() }
            assertTrue("kind $kind", result.stoppedCount >= 0 && result.failedCount >= 0)
            assertEquals(kind == "full", result.robots.isNotEmpty())
        }
    }

    @Test
    fun `ecosystem status of either kind is handed back whole`() {
        for (kind in listOf("minimal", "full")) {
            serve(200, answer("GET /api/ecosystem/status", "200", kind))
            val status = runBlocking { client.getEcosystemStatus() }
            assertTrue("kind $kind", status.has("projects"))
        }
    }

    @Test
    fun `system metrics of either kind are handed back whole`() {
        for (kind in listOf("minimal", "full")) {
            serve(200, answer("GET /api/system/metrics", "200", kind))
            val metrics = runBlocking { client.getSystemMetrics() }
            assertTrue("kind $kind", metrics.has("cpu_load"))
        }
    }

    @Test
    fun `a login answer of either kind carries the tokens the app stores`() {
        for (kind in listOf("minimal", "full")) {
            serve(200, answer("POST /api/login", "200", kind))
            val login = runBlocking { client.login("u", "p") }
            assertTrue("kind $kind", login.has("token") && login.has("refreshToken"))
        }
    }

    @Test
    fun `a refusal body of the login reaches the app as an error with the status`() {
        serve(401, answer("POST /api/login", "401", "minimal"))
        try {
            runBlocking { client.login("u", "p") }
            throw AssertionError("expected a HydraApiException")
        } catch (e: HydraApiException) {
            assertEquals(401, e.httpCode)
        }
    }
}
