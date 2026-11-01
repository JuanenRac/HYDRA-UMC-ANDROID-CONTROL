// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - app/src/test/java/com/hydraumc/control/network/RobotCommandRepositoryTest.kt
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.network

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

/** The command transport of the app on the JVM, against a fake server: revisions, retry of a stop, `table` fallback, debounce and timings. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RobotCommandRepositoryTest {

    private class Call(val robotId: Int, val payload: JSONObject, val urgent: Boolean, val ifMatch: String?)

    private class FakeServer(val handler: (Call, Int) -> JSONObject) : CommandPoster {
        val calls = mutableListOf<Call>()
        override suspend fun post(robotId: Int, payload: JSONObject, urgent: Boolean, ifMatch: String?): JSONObject {
            val call = Call(robotId, JSONObject(payload.toString()), urgent, ifMatch)
            calls += call
            return handler(call, calls.size)
        }
    }

    private fun payload(command: String, params: JSONObject? = null) =
        JSONObject().put("command", command).apply { if (params != null) put("params", params) }

    private fun repository(mode: ConditionalMode = ConditionalMode.STANDARD) =
        RobotCommandRepository(conditionalMode = { mode }, retryPauseMs = 1L)

    @Test
    fun theRevisionOfAnAnswerIsSentAsIfMatchOnTheNextWrite() = runBlocking {
        val repository = repository()
        val server = FakeServer { _, n -> JSONObject().put("success", true).put("revision", "rev$n") }
        repository.send(1, RobotCommandContract.PLAY, payload("play"), server)
        repository.send(1, RobotCommandContract.PAUSE, payload("pause"), server)
        assertNull(server.calls[0].ifMatch)
        assertEquals("\"rev1\"", server.calls[1].ifMatch)
    }

    @Test
    fun aStopNeverCarriesIfMatchAndGoesOutUrgent() = runBlocking {
        val repository = repository()
        val server = FakeServer { _, _ -> JSONObject().put("success", true).put("revision", "r") }
        repository.send(1, RobotCommandContract.PLAY, payload("play"), server)
        repository.send(1, RobotCommandContract.STOP, payload("stop"), server)
        assertNull(server.calls[1].ifMatch)
        assertTrue(server.calls[1].urgent)
        assertFalse(server.calls[0].urgent)
    }

    @Test
    fun aStopIsRetriedWhenNobodyAnswersButNotAfterAnAnswerOfTheServer() = runBlocking {
        val flaky = FakeServer { _, n -> if (n < 3) throw IOException("no route") else JSONObject().put("success", true) }
        repository().send(1, RobotCommandContract.STOP, payload("stop"), flaky)
        assertEquals(3, flaky.calls.size)

        val refusing = FakeServer { _, _ -> throw HydraApiException("refused", httpCode = 403) }
        try {
            repository().send(1, RobotCommandContract.STOP, payload("stop"), refusing)
        } catch (_: HydraApiException) {
        }
        assertEquals(1, refusing.calls.size)
    }

    @Test
    fun anOrdinaryCommandIsNotRetried() = runBlocking {
        val failing = FakeServer { _, _ -> throw IOException("down") }
        try {
            repository().send(1, RobotCommandContract.PLAY, payload("play"), failing)
        } catch (_: IOException) {
        }
        assertEquals(1, failing.calls.size)
    }

    @Test
    fun aMismatchTeachesTheRevisionTheServerIsAt() = runBlocking {
        val repository = repository()
        val server = FakeServer { _, n ->
            when (n) {
                1 -> JSONObject().put("success", true).put("revision", "old")
                2 -> throw HydraApiException("changed", httpCode = 412, revision = "new")
                else -> JSONObject().put("success", true)
            }
        }
        repository.send(1, RobotCommandContract.PLAY, payload("play"), server)
        try {
            repository.send(1, RobotCommandContract.PAUSE, payload("pause"), server)
        } catch (e: HydraApiException) {
            assertTrue(e.isRevisionMismatch)
        }
        assertEquals("new", repository.revisions.known(1))
    }

    @Test
    fun aTableMoveBecomesJogsOnAServerThatDoesNotKnowTheCommand() = runBlocking {
        val repository = repository()
        val server = FakeServer { call, _ ->
            if (call.payload.getString("command") == "table") {
                throw HydraApiException("bad", httpCode = 400, serverMessage = "unknown command: table")
            }
            JSONObject().put("success", true)
        }
        val params = JSONObject().put("mode", "relative").put("x", 5.0).put("y", 2.0)
        repository.send(1, RobotCommandContract.TABLE, payload("table", params), server)
        assertEquals(listOf("table", "jog", "jog"), server.calls.map { it.payload.getString("command") })
        assertFalse(repository.tableCommandSupported)
        // From now on the table move goes out as jogs at once.
        repository.send(1, RobotCommandContract.TABLE, payload("table", params), server)
        assertEquals(5, server.calls.size)
        assertEquals(listOf("jog", "jog"), server.calls.takeLast(2).map { it.payload.getString("command") })
    }

    @Test
    fun theTimesOfTheAnswersAreKeptPerCommand() = runBlocking {
        var clock = 0L
        val repository = RobotCommandRepository(nowMs = { clock }, retryPauseMs = 1L)
        val server = FakeServer { _, n ->
            clock += if (n == 1) 40L else 100L
            if (n == 3) throw HydraApiException("down", httpCode = 500)
            JSONObject().put("success", true)
        }
        repository.send(1, RobotCommandContract.JOG, payload("jog"), server)
        repository.send(1, RobotCommandContract.JOG, payload("jog"), server)
        try {
            repository.send(1, RobotCommandContract.JOG, payload("jog"), server)
        } catch (_: HydraApiException) {
        }
        val stats = repository.statsOf(RobotCommandContract.JOG)!!
        assertEquals(3, stats.sent)
        assertEquals(1, stats.failed)
        assertEquals(100L, stats.lastMs)
        assertEquals(100L, stats.maxMs)
        assertEquals(70L, stats.averageMs)
        assertNull(repository.statsOf(RobotCommandContract.PLAY))
    }

    @Test
    fun jogsOnTheWireAreCountedPerRobot() {
        val repository = repository()
        repository.jogStarted(1)
        repository.jogStarted(1)
        assertTrue(repository.hasJogInFlight(1))
        assertFalse(repository.hasJogInFlight(2))
        repository.jogEnded(1)
        assertTrue(repository.hasJogInFlight(1))
        repository.jogEnded(1)
        repository.jogEnded(1)
        assertFalse(repository.hasJogInFlight(1))
    }

    @Test
    fun aDebouncedCommandIsReplacedOnlyByTheSameCommand() = runTest {
        val repository = repository()
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        val sent = mutableListOf<String>()
        repository.debounce(scope, "speed", 300) { sent += "speed-1" }
        repository.debounce(scope, "jog", 0) { sent += "jog" }
        repository.debounce(scope, "speed", 300) { sent += "speed-2" }
        assertEquals(listOf("jog"), sent)
        scope.advanceTimeBy(301)
        scope.testScheduler.runCurrent()
        assertEquals(listOf("jog", "speed-2"), sent)
    }

    @Test
    fun cancellingDropsWhatWaits() = runTest {
        val repository = repository()
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        val sent = mutableListOf<String>()
        repository.debounce(scope, "speed", 300) { sent += "speed" }
        repository.cancelAllPending()
        scope.advanceTimeBy(1000)
        scope.testScheduler.runCurrent()
        assertTrue(sent.isEmpty())
    }

    @Test
    fun warningsOfAnAnswerAreJoinedAndAnEmptyListIsNothing() {
        assertNull(RobotCommandRepository.warningsOf(JSONObject("""{"success":true}""")))
        assertNull(RobotCommandRepository.warningsOf(JSONObject("""{"warnings":[]}""")))
        assertEquals(
            "speed out of range; plain",
            RobotCommandRepository.warningsOf(JSONObject("""{"warnings":[{"message":"speed out of range"},"plain"]}""")),
        )
    }
}
