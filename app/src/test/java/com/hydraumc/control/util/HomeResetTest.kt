// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - app/src/test/java/com/hydraumc/control/util/HomeResetTest.kt
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.util

import com.hydraumc.control.network.RobotCommandContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The rules of HOME/RESET: when the app refuses to offer it, and the confirmation in steps. Pure rules, no robot involved. */
class HomeResetTest {
    private val ready = HomeReset.Situation(
        switchedOn = true, connected = true, robotOnline = true, robotEnabled = true, robotPlaying = false,
        jogInFlight = false, stopInProgress = false, needsArming = false, role = "operator", hasPose = true, serverSupports = true,
    )

    @Test
    fun `it is off by default and the contract asks for two steps`() {
        assertFalse(RobotCommandContract.HOME_RESET_ENABLED_BY_DEFAULT)
        assertEquals(2, RobotCommandContract.HOME_RESET_CONFIRM_STEPS)
        assertTrue(RobotCommandContract.HOME_RESET_CONFIRM_WINDOW_MS > 0)
    }

    @Test
    fun `a ready situation has no block`() {
        assertEquals(HomeReset.Block.NONE, HomeReset.blockOf(ready))
        assertEquals(HomeReset.Block.NONE, HomeReset.blockOf(ready.copy(role = "admin")))
        // A role that cannot be read is left to the server, which decides.
        assertEquals(HomeReset.Block.NONE, HomeReset.blockOf(ready.copy(role = null)))
    }

    @Test
    fun `each reason blocks`() {
        assertEquals(HomeReset.Block.OFF, HomeReset.blockOf(ready.copy(switchedOn = false)))
        assertEquals(HomeReset.Block.NOT_CONNECTED, HomeReset.blockOf(ready.copy(connected = false)))
        assertEquals(HomeReset.Block.NOT_SUPPORTED, HomeReset.blockOf(ready.copy(serverSupports = false)))
        assertEquals(HomeReset.Block.ROLE, HomeReset.blockOf(ready.copy(role = "viewer")))
        assertEquals(HomeReset.Block.OFFLINE, HomeReset.blockOf(ready.copy(robotOnline = false)))
        assertEquals(HomeReset.Block.SAFE_MODE, HomeReset.blockOf(ready.copy(robotEnabled = false)))
        assertEquals(HomeReset.Block.STOP_PENDING, HomeReset.blockOf(ready.copy(stopInProgress = true)))
        assertEquals(HomeReset.Block.MOVING, HomeReset.blockOf(ready.copy(robotPlaying = true)))
        assertEquals(HomeReset.Block.JOG_BUSY, HomeReset.blockOf(ready.copy(jogInFlight = true)))
        assertEquals(HomeReset.Block.NOT_ARMED, HomeReset.blockOf(ready.copy(needsArming = true)))
        assertEquals(HomeReset.Block.NO_POSE, HomeReset.blockOf(ready.copy(hasPose = false)))
    }

    @Test
    fun `the switch wins over everything else`() {
        assertEquals(HomeReset.Block.OFF, HomeReset.blockOf(ready.copy(switchedOn = false, connected = false, robotPlaying = true)))
    }

    @Test
    fun `only the last step finishes`() {
        var now = 1_000L
        val confirmation = HomeReset.Confirmation(2, 20_000L) { now }
        assertEquals(HomeReset.Confirmation.Progress.Waiting(2), confirmation.advance())
        now += 5_000
        assertEquals(HomeReset.Confirmation.Progress.Done, confirmation.advance())
    }

    @Test
    fun `a step given after the window starts over`() {
        var now = 1_000L
        val confirmation = HomeReset.Confirmation(2, 20_000L) { now }
        confirmation.advance()
        now += 20_001
        assertEquals(1, confirmation.current())
        // The second press is taken as a first one: it is not Done.
        assertEquals(HomeReset.Confirmation.Progress.Waiting(2), confirmation.advance())
    }

    @Test
    fun `cancel and a finished confirmation both start over`() {
        val confirmation = HomeReset.Confirmation(2, 20_000L) { 1_000L }
        confirmation.advance()
        confirmation.cancel()
        assertEquals(HomeReset.Confirmation.Progress.Waiting(2), confirmation.advance())
        assertEquals(HomeReset.Confirmation.Progress.Done, confirmation.advance())
        // Done does not stay armed: the next press is a first step again.
        assertEquals(HomeReset.Confirmation.Progress.Waiting(2), confirmation.advance())
    }

    @Test
    fun `a single step is done at once and zero steps count as one`() {
        assertEquals(HomeReset.Confirmation.Progress.Done, HomeReset.Confirmation(1, 1_000L) { 0L }.advance())
        assertEquals(HomeReset.Confirmation.Progress.Done, HomeReset.Confirmation(0, 1_000L) { 0L }.advance())
    }
}
