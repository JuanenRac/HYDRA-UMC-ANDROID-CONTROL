// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - app/src/test/java/com/hydraumc/control/util/DeadManJogTest.kt
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.util

import com.hydraumc.control.network.RobotCommandContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The rules of the continuous jog held by a dead-man: when it is refused, the speed bounds and the watchdog. */
class DeadManJogTest {
    private fun ready() = DeadManJog.Situation(
        switchedOn = true, connected = true, robotOnline = true, robotEnabled = true, robotPlaying = false,
        stopInProgress = false, needsArming = false, role = "operator", linkBlocks = false, alreadyRunning = false, serverSupports = true,
    )

    @Test
    fun itIsOffUntilThePersonTurnsItOn() {
        assertEquals(false, RobotCommandContract.DEAD_MAN_JOG_ENABLED_BY_DEFAULT)
        assertEquals(DeadManJog.Block.OFF, DeadManJog.blockOf(ready().copy(switchedOn = false)))
        assertEquals(DeadManJog.Block.NONE, DeadManJog.blockOf(ready()))
    }

    @Test
    fun theNumbersComeFromTheContract() {
        val settings = DeadManJog.Settings()
        assertEquals(100L, settings.heartbeatMs)
        assertEquals(300L, settings.serverTimeoutMs)
        assertTrue(settings.serverTimeoutMs > settings.heartbeatMs)
    }

    @Test
    fun eachReasonToRefuseIsReported() {
        val cases = listOf(
            ready().copy(connected = false) to DeadManJog.Block.NOT_CONNECTED,
            ready().copy(serverSupports = false) to DeadManJog.Block.NOT_SUPPORTED,
            ready().copy(role = "viewer") to DeadManJog.Block.ROLE,
            ready().copy(robotOnline = false) to DeadManJog.Block.OFFLINE,
            ready().copy(robotEnabled = false) to DeadManJog.Block.SAFE_MODE,
            ready().copy(stopInProgress = true) to DeadManJog.Block.STOP_PENDING,
            ready().copy(robotPlaying = true) to DeadManJog.Block.MOVING,
            ready().copy(needsArming = true) to DeadManJog.Block.NOT_ARMED,
            ready().copy(alreadyRunning = true) to DeadManJog.Block.ALREADY_RUNNING,
            ready().copy(linkBlocks = true) to DeadManJog.Block.BAD_LINK,
        )
        for ((situation, block) in cases) assertEquals(block, DeadManJog.blockOf(situation))
    }

    @Test
    fun aRoleThatCannotBeReadIsLeftToTheServer() {
        assertEquals(DeadManJog.Block.NONE, DeadManJog.blockOf(ready().copy(role = null)))
        assertEquals(DeadManJog.Block.NONE, DeadManJog.blockOf(ready().copy(role = "admin")))
    }

    @Test
    fun theVelocityStaysInsideTheContractRange() {
        assertEquals(-5.0, DeadManJog.clampVelocity(-5.0), 0.0)
        assertEquals(-100.0, DeadManJog.clampVelocity(-1e9), 0.0)
        assertEquals(100.0, DeadManJog.clampVelocity(1e9), 0.0)
        assertEquals(1.0, DeadManJog.clampVelocity(0.0), 0.0)
        assertEquals(-1.0, DeadManJog.clampVelocity(-0.2), 0.0)
        assertEquals(1.0, DeadManJog.clampVelocity(Double.NaN), 0.0)
        assertEquals(42.5, DeadManJog.clampVelocity(42.5), 0.0)
    }

    @Test
    fun settingsThatCouldNotStopTheAxisAreRefused() {
        val refused = runCatching { DeadManJog.Settings(heartbeatMs = 300, serverTimeoutMs = 300) }
        assertTrue(refused.isFailure)
    }

    @Test
    fun theWatchdogTrustsTheLinkWhileHeartbeatsAreAnswered() {
        var now = 0L
        val dog = DeadManJog.Watchdog(DeadManJog.Settings()) { now }
        repeat(20) {
            now += 100
            dog.acked()
            assertNull(dog.verdict())
        }
    }

    @Test
    fun theWatchdogGivesTheLinkUpAfterTwoMissedHeartbeats() {
        var now = 0L
        val dog = DeadManJog.Watchdog(DeadManJog.Settings()) { now }
        now += 100
        dog.missed()
        assertNull(dog.verdict())
        now += 100
        dog.missed()
        assertEquals(DeadManJog.EndReason.LINK_LOST, dog.verdict())
    }

    @Test
    fun anAnswerClearsTheMissedCount() {
        var now = 0L
        val dog = DeadManJog.Watchdog(DeadManJog.Settings()) { now }
        now += 100
        dog.missed()
        now += 100
        dog.acked()
        assertEquals(0, dog.missedInARow())
        assertNull(dog.verdict())
    }

    @Test
    fun silenceLongerThanTheServerTimeoutIsALostLink() {
        var now = 0L
        val dog = DeadManJog.Watchdog(DeadManJog.Settings()) { now }
        now = 301
        assertEquals(DeadManJog.EndReason.LINK_LOST, dog.verdict())
    }

    @Test
    fun aHoldPastTheLimitEnds() {
        var now = 0L
        val dog = DeadManJog.Watchdog(DeadManJog.Settings(maxHoldMs = 1000)) { now }
        while (now < 1000) {
            now += 100
            dog.acked()
        }
        assertNull(dog.verdict())
        now += 100
        dog.acked()
        assertEquals(DeadManJog.EndReason.TOO_LONG, dog.verdict())
    }
}
