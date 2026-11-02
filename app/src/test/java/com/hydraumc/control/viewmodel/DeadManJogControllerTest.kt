// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - app/src/test/java/com/hydraumc/control/viewmodel/DeadManJogControllerTest.kt
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.viewmodel

import com.hydraumc.control.network.CommandPoster
import com.hydraumc.control.network.HydraApiException
import com.hydraumc.control.network.RobotCommandContract
import com.hydraumc.control.util.DeadManJog
import com.hydraumc.control.util.LinkHealth
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
 * The screen-facing side of the continuous jog against a fake server: the switch that is off by default, the refusals with
 * their phrases, the stop in every way a held control can end, and the link meter that blocks the jog.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DeadManJogControllerTest {
    private class Server : CommandPoster {
        val commands = mutableListOf<String>()
        var startError: Throwable? = null
        var heartbeatError: Throwable? = null
        override suspend fun post(robotId: Int, payload: JSONObject, urgent: Boolean, ifMatch: String?): JSONObject {
            val command = payload.getString("command")
            commands += command
            if (command == RobotCommandContract.JOG_START) startError?.let { throw it }
            if (command == RobotCommandContract.JOG_HEARTBEAT) heartbeatError?.let { throw it }
            return JSONObject()
        }

        fun count(command: String) = commands.count { it == command }
    }

    private class Fixture(scope: TestScope, val server: Server = Server()) {
        val said = mutableListOf<String?>()
        var pingMs = 20L
        var pingFails = false
        var robotPlaying = false
        val controller = DeadManJogController(
            scope = scope.backgroundScope,
            poster = server,
            pinger = { if (pingFails) throw HydraApiException("no answer") else pingMs },
            situation = { _, on, running, linkBlocks, supports ->
                DeadManJog.Situation(
                    switchedOn = on, connected = true, robotOnline = true, robotEnabled = true, robotPlaying = robotPlaying,
                    stopInProgress = false, needsArming = false, role = "operator", linkBlocks = linkBlocks,
                    alreadyRunning = running, serverSupports = supports,
                )
            },
            say = { said += it },
            blockText = { "blocked:${it.name}" },
            endText = { reason, confirmed -> if (reason == DeadManJog.EndReason.RELEASED) null else "ended:${reason.name}:$confirmed" },
            errorText = { "error:${it.javaClass.simpleName}" },
            clock = { scope.testScheduler.currentTime },
            probeIntervalMs = 1_000,
        )
    }

    @Test
    fun theContinuousJogIsOffByDefaultAndRefusesWithItsOwnPhrase() = runTest {
        val f = Fixture(this)
        assertFalse(f.controller.enabled.value)
        assertTrue(f.controller.blockOnBadLink.value)
        f.controller.press(1, "robot", "x", 1)
        runCurrent()
        assertEquals(listOf<String?>("blocked:OFF"), f.said)
        assertEquals(0, f.server.commands.size)
        assertTrue(f.controller.held.value.isEmpty())
    }

    @Test
    fun whenOnItStartsHeartbeatsAndStopsOnRelease() = runTest {
        val f = Fixture(this)
        f.controller.enabled.value = true
        f.controller.velocity.value = 30f
        f.controller.press(1, "robot", "y", -1)
        runCurrent()
        assertEquals(setOf(1), f.controller.held.value)
        advanceTimeBy(450)
        f.controller.release(1)
        runCurrent()
        assertEquals(1, f.server.count(RobotCommandContract.JOG_START))
        assertTrue(f.server.count(RobotCommandContract.JOG_HEARTBEAT) >= 4)
        assertEquals(1, f.server.count(RobotCommandContract.JOG_STOP))
        assertTrue(f.controller.held.value.isEmpty())
    }

    @Test
    fun aReleaseWhileTheStartIsOnTheWireStopsAtOnce() = runTest {
        val f = Fixture(this)
        f.controller.enabled.value = true
        f.controller.press(1, "robot", "x", 1)
        f.controller.release(1)
        runCurrent()
        advanceTimeBy(50)
        runCurrent()
        assertEquals(f.server.count(RobotCommandContract.JOG_START), f.server.count(RobotCommandContract.JOG_STOP))
        assertTrue(f.controller.held.value.isEmpty())
    }

    @Test
    fun aSecondPressOfAHeldRobotIsRefused() = runTest {
        val f = Fixture(this)
        f.controller.enabled.value = true
        f.controller.press(1, "robot", "x", 1)
        runCurrent()
        f.controller.press(1, "robot", "y", 1)
        runCurrent()
        assertEquals("blocked:ALREADY_RUNNING", f.said.last())
        assertEquals(1, f.server.count(RobotCommandContract.JOG_START))
        f.controller.release(1)
        runCurrent()
    }

    @Test
    fun aTrajectoryThatPlaysBlocksTheJog() = runTest {
        val f = Fixture(this)
        f.controller.enabled.value = true
        f.robotPlaying = true
        f.controller.press(1, "robot", "x", 1)
        runCurrent()
        assertEquals("blocked:MOVING", f.said.single())
        assertEquals(0, f.server.commands.size)
    }

    @Test
    fun aServerThatDoesNotKnowTheCommandsIsRememberedAndExplained() = runTest {
        val f = Fixture(this)
        f.controller.enabled.value = true
        f.server.startError = HydraApiException("unknown command", httpCode = 400, serverMessage = "unknown command")
        f.controller.press(1, "robot", "x", 1)
        runCurrent()
        assertFalse(f.controller.serverSupports.value)
        assertTrue(f.controller.held.value.isEmpty())
        f.controller.press(1, "robot", "x", 1)
        runCurrent()
        assertEquals("blocked:NOT_SUPPORTED", f.said.last())
        assertEquals(1, f.server.count(RobotCommandContract.JOG_START))
    }

    @Test
    fun aLostLinkMarksTheControlAsNotHeldAndTellsWhy() = runTest {
        val f = Fixture(this)
        f.controller.enabled.value = true
        f.controller.press(1, "robot", "x", 1)
        runCurrent()
        f.server.heartbeatError = java.io.IOException("down")
        advanceTimeBy(500)
        runCurrent()
        assertTrue(f.controller.held.value.isEmpty())
        assertTrue(f.said.any { it == "ended:LINK_LOST:true" })
        assertEquals(1, f.server.count(RobotCommandContract.JOG_STOP))
    }

    @Test
    fun interruptingStopsTheHeldJog() = runTest {
        val f = Fixture(this)
        f.controller.enabled.value = true
        f.controller.press(1, "robot", "x", 1)
        runCurrent()
        advanceTimeBy(250)
        f.controller.interrupt()
        runCurrent()
        assertEquals(1, f.server.count(RobotCommandContract.JOG_STOP))
        assertTrue(f.controller.held.value.isEmpty())
    }

    @Test
    fun theLinkIndicatorFollowsTheProbeAndABadLinkBlocksTheJog() = runTest {
        val f = Fixture(this)
        f.controller.enabled.value = true
        var connected = true
        f.controller.ensureProbe { connected }
        advanceTimeBy(100)
        runCurrent()
        assertEquals(LinkHealth.Level.GOOD, f.controller.linkLevel.value)
        assertEquals(20L, f.controller.linkRttMs.value)
        f.pingMs = 700
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(LinkHealth.Level.BAD, f.controller.linkLevel.value)
        f.controller.press(1, "robot", "x", 1)
        runCurrent()
        assertEquals("blocked:BAD_LINK", f.said.last())
        // with the switch off the same link no longer blocks
        f.controller.blockOnBadLink.value = false
        f.controller.press(1, "robot", "x", 1)
        runCurrent()
        assertEquals(setOf(1), f.controller.held.value)
        f.controller.release(1)
        connected = false
        advanceTimeBy(2_000)
        runCurrent()
        assertEquals(LinkHealth.Level.UNKNOWN, f.controller.linkLevel.value)
        assertNull(f.controller.linkRttMs.value)
    }

    @Test
    fun aProbeThatGetsNoAnswerMakesTheLinkLost() = runTest {
        val f = Fixture(this)
        f.controller.ensureProbe { true }
        advanceTimeBy(100)
        f.pingFails = true
        advanceTimeBy(3_000)
        runCurrent()
        assertEquals(LinkHealth.Level.LOST, f.controller.linkLevel.value)
        f.controller.stopProbe()
    }
}
