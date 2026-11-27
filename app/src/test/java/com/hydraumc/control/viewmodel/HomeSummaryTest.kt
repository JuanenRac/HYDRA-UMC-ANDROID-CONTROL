// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - app/src/test/java/com/hydraumc/control/viewmodel/HomeSummaryTest.kt
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.viewmodel

import com.hydraumc.control.network.WsStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeSummaryTest {
    private fun robot(online: Boolean = true, enabled: Boolean = true, playing: Boolean = false, paused: Boolean = false) = RobotState(
        id = 1, name = "A", model = "Parol6", manufacturer = "", role = "", online = online, isPlaying = playing, isPaused = paused, isEnabled = enabled,
        activeStep = -1, hasXYTable = false, hasAtc = false, hasCamera = false, hasPnP = false, hasCNC = false, hasLaser = false,
        hasHeatedBed = false, hasVacuumTable = false, hasRack = false, combinedWith = emptyList(), currentTool = "", atcTools = emptyList(),
        speed = 100.0, acceleration = 100.0, posX = 0.0, posY = 0.0, posZ = 0.0, xyPosX = 0.0, xyPosY = 0.0,
        j1 = 0.0, j2 = 0.0, j3 = 0.0, j4 = 0.0, j5 = 0.0, j6 = 0.0, endstops = emptyMap(), valves = emptyList(), pumps = emptyList(),
        recordedPointsCount = 0, jogStep = 1.0,
    )

    @Test
    fun withoutAConnectionNothingIsCalledReady() {
        assertEquals(HomeState.NotConnected, HomeState.of(WsStatus.DISCONNECTED, listOf(robot())))
        assertEquals(HomeState.Connecting, HomeState.of(WsStatus.CONNECTING, listOf(robot())))
    }

    @Test
    fun theWorstThingWins() {
        val all = listOf(robot(enabled = false), robot(online = false), robot(playing = true), robot())
        assertEquals(HomeState.SafeMode(1), HomeState.of(WsStatus.CONNECTED, all))
        assertEquals(HomeState.Offline(1), HomeState.of(WsStatus.CONNECTED, all.drop(1)))
        assertEquals(HomeState.Moving(1), HomeState.of(WsStatus.CONNECTED, all.drop(2)))
        assertEquals(HomeState.Ready(1), HomeState.of(WsStatus.CONNECTED, all.drop(3)))
        assertEquals(HomeState.NoRobots, HomeState.of(WsStatus.CONNECTED, emptyList()))
    }

    @Test
    fun aPausedRobotIsNotMoving() {
        assertEquals(HomeState.Ready(1), HomeState.of(WsStatus.CONNECTED, listOf(robot(playing = true, paused = true))))
    }

    @Test
    fun whatIsNotKnownIsNeverPending() {
        val steps = SetupSteps.of(server = true, controller = null, robot = false, secondFactor = null)
        assertEquals(listOf(SetupStep.ROBOT), steps.pending)
        assertEquals(1, steps.done)
        assertEquals(2, steps.known)
    }
}
