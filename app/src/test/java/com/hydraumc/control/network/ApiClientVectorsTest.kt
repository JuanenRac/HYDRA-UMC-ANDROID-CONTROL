// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - app/src/test/java/com/hydraumc/control/network/ApiClientVectorsTest.kt
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.network

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Replays the behaviour the Flutter apps share with this one (the SDK's conformance/api-client/vectors.json, copied to
 * the test resources): which commands are numbered and how the number grows, and how the server's refusals are read.
 * The renewal conversation is not replayed here: this app renews through its own OkHttp authenticator (SessionRenewerTest).
 */
@RunWith(RobolectricTestRunner::class)
class ApiClientVectorsTest {

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
        CommandSequence.reset()
        server.shutdown()
    }

    private fun doc(): JSONObject {
        val text = javaClass.classLoader!!.getResourceAsStream("api-client-vectors.json")!!.bufferedReader().readText()
        return JSONObject(text)
    }

    private fun strings(array: JSONArray): Set<String> = (0 until array.length()).map { array.getString(it) }.toSet()

    @Test
    fun theVectorFileIsTheVersionThisTestKnows() {
        assertEquals(1, doc().getInt("version"))
    }

    @Test
    fun theNumberedCommandsAndTheNumbersAreTheSharedOnes() {
        val sequence = doc().getJSONObject("sequence")
        for (command in strings(sequence.getJSONArray("numbered"))) assertTrue(command, CommandSequence.isNumbered(command))
        val steps = sequence.getJSONArray("steps")
        for (i in 0 until steps.length()) {
            val step = steps.getJSONObject(i)
            val command = step.getString("command")
            val expected = if (step.isNull("seq")) null else step.getLong("seq")
            val actual = if (CommandSequence.isNumbered(command)) CommandSequence.next { step.getLong("clock") } else null
            assertEquals("$command at ${step.getLong("clock")}", expected, actual)
        }
    }

    @Test
    fun everyRefusalIsReadAsTheVectorsSay() {
        val errors = doc().getJSONArray("errors")
        for (i in 0 until errors.length()) {
            val v = errors.getJSONObject(i)
            server.enqueue(MockResponse().setResponseCode(v.getInt("status")).setBody(v.getString("body")))
            val e = try {
                runBlocking { client.getSettings() }
                fail("expected a HydraApiException")
                return
            } catch (e: HydraApiException) {
                e
            }
            val label = "${v.getInt("status")} ${v.getString("body")}"
            assertEquals(label, v.getInt("status"), e.httpCode)
            assertEquals(label, if (v.isNull("code")) null else v.getString("code"), e.serverCode)
            assertEquals(label, if (v.has("revision")) v.getString("revision") else null, e.revision)
            assertEquals(label, if (v.has("serverMessage")) v.getString("serverMessage") else null, e.serverMessage)
            val flags = mapOf(
                "isRevisionMismatch" to e.isRevisionMismatch,
                "isSessionProblem" to e.isSessionProblem,
                "isPermissionDenied" to e.isPermissionDenied,
                "isReservedByAnother" to e.isReservedByAnother,
                "isOtherAccount" to e.isOtherAccount,
                "isStaleSequence" to e.isStaleSequence,
                "isOtherController" to e.isOtherController,
                "isControllerIdRequired" to e.isControllerIdRequired,
                "isJogNotArmed" to e.isJogNotArmed,
                "isHomeRejected" to e.isHomeRejected,
                "isRobotDisabled" to e.isRobotDisabled,
                "isRejectedCommand" to e.isRejectedCommand,
                "isRateLimited" to e.isRateLimited,
                "isNotFound" to e.isNotFound,
            )
            assertEquals(label, strings(v.getJSONArray("flags")), flags.filterValues { it }.keys)
        }
    }
}
