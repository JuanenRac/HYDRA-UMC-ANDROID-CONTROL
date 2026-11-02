// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - app/src/test/java/com/hydraumc/control/network/DeadManJogRunnerTest.kt
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.network

import com.hydraumc.control.util.DeadManJog
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
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

/**
 * The continuous jog against a fake server and the virtual clock: no robot is involved. It checks the order of the
 * three commands, the rhythm of the heartbeat and, above all, that the axis is stopped in every way a session can end.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DeadManJogRunnerTest {
    private class FakeServer : CommandPoster {
        val log = mutableListOf<Pair<String, JSONObject>>()
        var failHeartbeats: Throwable? = null
        var hangHeartbeats = false
        var stopFailures = 0
        var startFailure: Throwable? = null

        override suspend fun post(robotId: Int, payload: JSONObject, urgent: Boolean, ifMatch: String?): JSONObject {
            val command = payload.getString("command")
            log += command to payload.getJSONObject("params")
            when (command) {
                RobotCommandContract.JOG_START -> startFailure?.let { throw it }
                RobotCommandContract.JOG_HEARTBEAT -> {
                    if (hangHeartbeats) delay(60_000)
                    failHeartbeats?.let { throw it }
                }
                RobotCommandContract.JOG_STOP -> if (stopFailures > 0) {
                    stopFailures--
                    throw java.io.IOException("no answer")
                }
            }
            return JSONObject().put("ok", true)
        }

        fun count(command: String) = log.count { it.first == command }
    }

    private class Ended(val reason: DeadManJog.EndReason, val confirmed: Boolean)

    private fun TestScope.runner(
        server: FakeServer,
        ended: MutableList<Ended>,
        settings: DeadManJog.Settings = DeadManJog.Settings(),
        linkAllows: () -> Boolean = { true },
        samples: MutableList<Long> = mutableListOf(),
    ) = DeadManJogRunner(
        scope = backgroundScope, poster = server, settings = settings, clock = { testScheduler.currentTime },
        linkAllows = linkAllows, onRoundTrip = { samples += it }, onEnded = { _, reason, confirmed -> ended += Ended(reason, confirmed) },
    )

    @Test
    fun startThenHeartbeatsThenStopOnRelease() = runTest {
        val server = FakeServer()
        val ended = mutableListOf<Ended>()
        val runner = runner(server, ended)
        runner.start(1, "robot", "x", 20.0)
        assertTrue(runner.isRunning(1))
        assertEquals(RobotCommandContract.JOG_START, server.log.first().first)
        advanceTimeBy(1_000)
        runCurrent()
        // one heartbeat every 100 ms
        assertTrue("heartbeats: ${server.count(RobotCommandContract.JOG_HEARTBEAT)}", server.count(RobotCommandContract.JOG_HEARTBEAT) in 9..11)
        runner.release(1)
        runCurrent()
        assertEquals(1, server.count(RobotCommandContract.JOG_STOP))
        assertEquals(RobotCommandContract.JOG_STOP, server.log.last().first)
        assertEquals(DeadManJog.EndReason.RELEASED, ended.single().reason)
        assertTrue(ended.single().confirmed)
        assertFalse(runner.isRunning(1))
        val heartbeats = server.count(RobotCommandContract.JOG_HEARTBEAT)
        advanceTimeBy(1_000)
        assertEquals("no heartbeat after the release", heartbeats, server.count(RobotCommandContract.JOG_HEARTBEAT))
    }

    @Test
    fun theSessionIdTravelsInEveryCommandAndTheSpeedIsBounded() = runTest {
        val server = FakeServer()
        val runner = runner(server, mutableListOf())
        runner.start(2, "robot", "z", 5_000.0)
        advanceTimeBy(250)
        runner.release(2)
        runCurrent()
        val ids = server.log.map { it.second.getString("sessionId") }.toSet()
        assertEquals(1, ids.size)
        assertEquals(100.0, server.log.first().second.getDouble("velocity"), 0.0)
        assertEquals(100L, server.log.first().second.getLong("heartbeatMs"))
        assertEquals(300L, server.log.first().second.getLong("timeoutMs"))
        val sequences = server.log.filter { it.first == RobotCommandContract.JOG_HEARTBEAT }.map { it.second.getLong("sequence") }
        assertEquals(sequences.sorted(), sequences)
        assertEquals(sequences.size, sequences.toSet().size)
    }

    @Test
    fun failedHeartbeatsStopTheAxisByThemselves() = runTest {
        val server = FakeServer()
        val ended = mutableListOf<Ended>()
        val runner = runner(server, ended)
        runner.start(1, "robot", "x", 20.0)
        server.failHeartbeats = java.io.IOException("link down")
        advanceTimeBy(400)
        runCurrent()
        assertEquals(DeadManJog.EndReason.LINK_LOST, ended.single().reason)
        assertEquals(1, server.count(RobotCommandContract.JOG_STOP))
        assertFalse(runner.isRunning(1))
    }

    @Test
    fun aHeartbeatThatNeverAnswersStopsTheAxisBeforeTheHoldCouldContinue() = runTest {
        val server = FakeServer()
        val ended = mutableListOf<Ended>()
        val runner = runner(server, ended)
        runner.start(1, "robot", "x", 20.0)
        server.hangHeartbeats = true
        advanceTimeBy(700)
        runCurrent()
        assertEquals(DeadManJog.EndReason.LINK_LOST, ended.single().reason)
        assertEquals(1, server.count(RobotCommandContract.JOG_STOP))
    }

    @Test
    fun aRefusalOfTheServerEndsTheSessionAtOnce() = runTest {
        val server = FakeServer()
        val ended = mutableListOf<Ended>()
        val runner = runner(server, ended)
        runner.start(1, "robot", "x", 20.0)
        server.failHeartbeats = HydraApiException("reserved", httpCode = 409)
        advanceTimeBy(150)
        runCurrent()
        assertEquals(DeadManJog.EndReason.SERVER_REFUSED, ended.single().reason)
        assertEquals(1, server.count(RobotCommandContract.JOG_STOP))
    }

    @Test
    fun aBadLinkEndsTheSession() = runTest {
        val server = FakeServer()
        val ended = mutableListOf<Ended>()
        var allowed = true
        val runner = runner(server, ended, linkAllows = { allowed })
        runner.start(1, "robot", "x", 20.0)
        advanceTimeBy(300)
        allowed = false
        advanceTimeBy(200)
        runCurrent()
        assertEquals(DeadManJog.EndReason.BAD_LINK, ended.single().reason)
    }

    @Test
    fun aHoldPastTheLimitEnds() = runTest {
        val server = FakeServer()
        val ended = mutableListOf<Ended>()
        val runner = runner(server, ended, settings = DeadManJog.Settings(maxHoldMs = 1_000))
        runner.start(1, "robot", "x", 20.0)
        advanceTimeBy(1_500)
        runCurrent()
        assertEquals(DeadManJog.EndReason.TOO_LONG, ended.single().reason)
        assertEquals(1, server.count(RobotCommandContract.JOG_STOP))
    }

    @Test
    fun interruptingStopsEveryAxis() = runTest {
        val server = FakeServer()
        val ended = mutableListOf<Ended>()
        val runner = runner(server, ended)
        runner.start(1, "robot", "x", 20.0)
        runner.start(2, "robot", "y", 20.0)
        advanceTimeBy(250)
        runner.interrupt()
        runCurrent()
        assertEquals(2, server.count(RobotCommandContract.JOG_STOP))
        assertEquals(listOf(DeadManJog.EndReason.INTERRUPTED, DeadManJog.EndReason.INTERRUPTED), ended.map { it.reason })
        assertFalse(runner.anyRunning())
    }

    @Test
    fun aStopThatDoesNotArriveIsTriedAgainAndReportedAsNotConfirmedIfItNeverDoes() = runTest {
        val server = FakeServer()
        val ended = mutableListOf<Ended>()
        val runner = runner(server, ended)
        runner.start(1, "robot", "x", 20.0)
        server.stopFailures = 2
        runner.release(1)
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(3, server.count(RobotCommandContract.JOG_STOP))
        assertTrue(ended.single().confirmed)

        val dead = FakeServer()
        val endedDead = mutableListOf<Ended>()
        val second = runner(dead, endedDead)
        second.start(3, "robot", "x", 20.0)
        dead.stopFailures = 99
        second.release(3)
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(3, dead.count(RobotCommandContract.JOG_STOP))
        assertFalse(endedDead.single().confirmed)
    }

    @Test
    fun aStartTheServerRefusesLeavesNothingRunning() = runTest {
        val server = FakeServer()
        server.startFailure = HydraApiException("unknown command", httpCode = 400, serverMessage = "unknown command")
        val runner = runner(server, mutableListOf())
        val failure = runCatching { runner.start(1, "robot", "x", 20.0) }.exceptionOrNull()
        assertTrue(failure is HydraApiException)
        assertFalse(runner.isRunning(1))
        advanceTimeBy(500)
        assertEquals(0, server.count(RobotCommandContract.JOG_HEARTBEAT))
        assertEquals(0, server.count(RobotCommandContract.JOG_STOP))
    }

    @Test
    fun theRoundTripOfEachHeartbeatIsReported() = runTest {
        val server = FakeServer()
        val samples = mutableListOf<Long>()
        val runner = runner(server, mutableListOf(), samples = samples)
        runner.start(1, "robot", "x", 20.0)
        advanceTimeBy(350)
        runCurrent()
        assertTrue(samples.size >= 3)
        assertNull(samples.firstOrNull { it < 0 })
        runner.release(1)
    }

    @Test
    fun aSecondStartOfTheSameRobotDoesNothing() = runTest {
        val server = FakeServer()
        val runner = runner(server, mutableListOf())
        runner.start(1, "robot", "x", 20.0)
        runner.start(1, "robot", "y", 20.0)
        assertEquals(1, server.count(RobotCommandContract.JOG_START))
        runner.release(1)
        runCurrent()
    }
}
