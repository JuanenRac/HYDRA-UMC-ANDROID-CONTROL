// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - app/src/test/java/com/hydraumc/control/network/OkHttpHydraTransportTest.kt
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D)
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.network

import hydra.umc.client.HydraReply
import hydra.umc.client.HydraRequest
import kotlinx.coroutines.runBlocking
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
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
import java.util.concurrent.TimeUnit

/**
 * The transport that carries the typed client of the SDK over the HTTP client of the app: what goes out (token, headers, query, body), what comes back
 * (the decoded JSON model, the declared response headers) and that a refusal, a network failure and an answer of the wrong shape reach the caller as the
 * [HydraApiException] the hand-written calls always threw.
 */
@RunWith(RobolectricTestRunner::class)
class OkHttpHydraTransportTest {

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

    /** The plain exchange of the tests: a successful answer gives its text and headers, any other one is an exception with its status. */
    private val exchange: (Request, OkHttpClient) -> Pair<String, Headers> = { request, http ->
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw HydraApiException("HTTP ${response.code}", httpCode = response.code)
            (response.body?.string() ?: "") to response.headers
        }
    }

    private fun transport(token: String? = "tok.en") =
        OkHttpHydraTransport({ "http://127.0.0.1:${server.port}" }, { token }, { HydraApiClient.sharedHttpClient }, exchange)

    private fun send(request: HydraRequest, token: String? = "tok.en"): Any? = runBlocking { transport(token).send(request) }

    private fun failure(block: suspend () -> Any?): HydraApiException {
        try {
            runBlocking { block() }
        } catch (e: HydraApiException) {
            return e
        }
        fail("expected a HydraApiException")
        throw IllegalStateException()
    }

    private fun get(path: String, access: String = "user", vararg extra: Pair<String, String>) =
        HydraRequest("GET", path, extra.toMap(), null, access)

    @Test
    fun `a user route sends the bearer token and a public route sends none`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        send(get("/api/a"))
        send(get("/api/b", access = "public"))
        assertEquals("Bearer tok.en", server.takeRequest().getHeader("Authorization"))
        assertNull(server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun `a missing token goes out as an empty bearer like the calls written by hand`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        send(get("/api/a"), token = null)
        assertTrue(server.takeRequest().getHeader("Authorization").orEmpty().startsWith("Bearer"))
    }

    @Test
    fun `the query, the headers and the JSON body reach the server`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        val body = mapOf("n" to 1L, "text" to "a b", "list" to listOf("p", null), "inner" to mapOf("k" to true))
        send(HydraRequest("POST", "/api/x", mapOf("limit" to "5"), body, "user", headers = mapOf("If-Match" to "\"r1\"", "X-Controller-Id" to "phone-1")))
        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals("/api/x?limit=5", recorded.path)
        assertEquals("\"r1\"", recorded.getHeader("If-Match"))
        assertEquals("phone-1", recorded.getHeader("X-Controller-Id"))
        assertTrue(recorded.getHeader("Content-Type").orEmpty().startsWith("application/json"))
        val sent = JSONObject(recorded.body.readUtf8())
        assertEquals(1, sent.getInt("n"))
        assertEquals("a b", sent.getString("text"))
        assertEquals("p", sent.getJSONArray("list").getString(0))
        assertTrue(sent.getJSONArray("list").isNull(1))
        assertTrue(sent.getJSONObject("inner").getBoolean("k"))
    }

    @Test
    fun `a post without a body goes out as an empty object and a get without a body`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        send(HydraRequest("POST", "/api/x", emptyMap(), null, "user"))
        send(get("/api/y"))
        assertEquals("{}", server.takeRequest().body.readUtf8())
        val second = server.takeRequest()
        assertEquals("GET", second.method)
        assertEquals(0L, second.bodySize)
    }

    @Test
    fun `the answer is the decoded JSON model and whole numbers are Long`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"n":5,"d":1.5,"s":"x","b":true,"z":null,"list":[1,{"k":2}],"big":12345678901}"""))
        val model = send(get("/api/a")) as Map<*, *>
        assertEquals(5L, model["n"])
        assertEquals(1.5, model["d"])
        assertEquals("x", model["s"])
        assertEquals(true, model["b"])
        assertTrue(model.containsKey("z") && model["z"] == null)
        assertEquals(listOf(1L, mapOf("k" to 2L)), model["list"])
        assertEquals(12345678901L, model["big"])
    }

    @Test
    fun `an empty answer is null and an answer cut in the middle is an error`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody(""))
        assertNull(send(get("/api/a")))
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"a":"""))
        assertTrue(failure { transport().send(get("/api/a")) }.httpCode == null)
    }

    @Test
    fun `a route with response headers gets a reply with the declared headers only`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"v":1}""").setHeader("ETag", "\"abc\"").setHeader("X-Other", "no"))
        val reply = send(HydraRequest("GET", "/api/a", emptyMap(), null, "user", replyHeaders = listOf("ETag", "X-Next-Cursor"))) as HydraReply<*>
        assertEquals(mapOf("ETag" to "\"abc\""), reply.headers)
        assertEquals(mapOf("v" to 1L), reply.body)
    }

    @Test
    fun `a text answer is handed back as text and a binary one is refused`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("line 1\nline 2"))
        assertEquals("line 1\nline 2", send(HydraRequest("GET", "/api/a", emptyMap(), null, "user", responseType = "text")))
        server.enqueue(MockResponse().setResponseCode(200).setBody("bytes"))
        assertTrue(failure { transport().send(HydraRequest("GET", "/api/a", emptyMap(), null, "user", responseType = "binary")) }.message.orEmpty().contains("binary"))
    }

    @Test
    fun `a body that is not JSON goes out as written with its media type`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        send(HydraRequest("PUT", "/api/a", emptyMap(), "plain words", "user", contentType = "text/plain"))
        val recorded = server.takeRequest()
        assertEquals("plain words", recorded.body.readUtf8())
        assertTrue(recorded.getHeader("Content-Type").orEmpty().startsWith("text/plain"))
    }

    @Test
    fun `a path that is not an address is an error and not a crash`() {
        val broken = OkHttpHydraTransport({ "not an address" }, { "t" }, { HydraApiClient.sharedHttpClient }, exchange)
        assertTrue(failure { broken.send(get("/api/a")) }.message.orEmpty().contains("address"))
    }

    @Test
    fun `decode and encode are the two ways of the same model`() {
        val model = OkHttpHydraTransport.decode("""{"a":[1,2.5,"x",null,{"b":false}]}""") as Map<*, *>
        val json = OkHttpHydraTransport.encode(model) as JSONObject
        assertEquals(1, json.getJSONArray("a").getInt(0))
        assertEquals(2.5, json.getJSONArray("a").getDouble(1), 0.0)
        assertTrue(json.getJSONArray("a").isNull(3))
        assertEquals(false, json.getJSONArray("a").getJSONObject(4).getBoolean("b"))
    }

    // ---- through the client: the errors a caller tells apart -------------------------------------------------

    @Test
    fun `a refusal of a typed call keeps the status, the code and the text`() {
        server.enqueue(MockResponse().setResponseCode(409).setBody("""{"error":"held by another phone","code":"other-controller"}"""))
        val error = failure { client.postJogArm(1, true) }
        assertEquals(409, error.httpCode)
        assertTrue(error.isOtherController)
        assertEquals("held by another phone", error.serverMessage)
    }

    @Test
    fun `an expired session on a typed call is still a session problem`() {
        server.enqueue(MockResponse().setResponseCode(403).setBody("""{"error":"expired","code":"TOKEN_EXPIRED"}"""))
        assertTrue(failure { client.getSystemMetrics() }.isSessionProblem)
    }

    @Test
    fun `a typed call to a server that is not listening is an error without a status`() {
        val port = server.port
        server.shutdown()
        val dead = HydraApiClient("127.0.0.1", port)
        assertNull(failure { dead.getEcosystemStatus() }.httpCode)
        server = MockWebServer().also { it.start() } // the tear-down shuts a server down
    }

    @Test
    fun `a typed call keeps the time limit of the client it was given`() {
        val quick = HydraApiClient("127.0.0.1", server.port, HydraApiClient.sharedHttpClient.newBuilder().callTimeout(400, TimeUnit.MILLISECONDS).build())
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}").setBodyDelay(5, TimeUnit.SECONDS))
        val started = System.currentTimeMillis()
        val error = failure { quick.getSystemMetrics() }
        assertNull(error.httpCode)
        assertTrue("took ${System.currentTimeMillis() - started} ms", System.currentTimeMillis() - started < 4_000)
    }

    @Test
    fun `an answer of the wrong shape is an error of the client and not a crash`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        val error = failure { client.getSystemMetrics() }
        assertNull(error.httpCode)
        assertTrue(error.message.orEmpty().contains("contract"))
        server.enqueue(MockResponse().setResponseCode(200).setBody("[1,2]"))
        assertTrue(failure { client.getEcosystemStatus() }.message.orEmpty().contains("contract"))
    }

    @Test
    fun `the typed jog arm names the controller in the header and in the body and reads the time to live`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"success":true,"armed":true,"ttlMs":45000}"""))
        val answer = runBlocking { client.postJogArm(3, true) }
        assertEquals(45000L, answer.optLong("ttlMs"))
        val recorded = server.takeRequest()
        assertEquals("/api/robot/3/jog-arm", recorded.path)
        assertEquals("POST", recorded.method)
        assertEquals("Bearer tok.en", recorded.getHeader("Authorization"))
        assertEquals(SessionController.id, recorded.getHeader("X-Controller-Id"))
        assertEquals("android/${com.hydraumc.control.BuildConfig.VERSION_NAME}", recorded.getHeader("X-Hydra-Client"))
        val sent = JSONObject(recorded.body.readUtf8())
        assertEquals(true, sent.getBoolean("armed"))
        assertEquals(SessionController.id, sent.getString("controllerId"))
    }

    @Test
    fun `the typed system metrics and ecosystem status keep the fields their callers read`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"cpu_load":12.5,"memory_usage":34.0,"temp":null,"temp_is_real":false,"rp1_temp":null,"uptime":99.0,"network":{"eth0":"up"}}"""))
        val metrics = runBlocking { client.getSystemMetrics() }
        assertEquals(12, metrics.optInt("cpu_load"))
        assertTrue(metrics.isNull("temp"))
        assertEquals("up", metrics.getJSONObject("network").getString("eth0"))
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"available":true,"scannedAt":"t","projects":[{"name":"P","servicePort":8080,"live":true}]}"""))
        val ecosystem = runBlocking { client.getEcosystemStatus() }
        assertTrue(ecosystem.getBoolean("available"))
        val project = ecosystem.getJSONArray("projects").getJSONObject(0)
        assertEquals("P", project.getString("name"))
        assertEquals(8080, project.optInt("servicePort"))
        assertTrue(project.isNull("role"))
        assertEquals("/api/system/metrics", server.takeRequest().path)
        assertEquals("/api/ecosystem/status", server.takeRequest().path)
    }
}
