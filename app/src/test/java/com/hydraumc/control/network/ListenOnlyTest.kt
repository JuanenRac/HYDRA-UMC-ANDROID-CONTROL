// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - app/src/test/java/com/hydraumc/control/network/ListenOnlyTest.kt
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.network

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Listen only: off lets everything through; on lets only STOP and the safe-mode disable go out. */
class ListenOnlyTest {
    @Test
    fun `off lets every command of the contract through`() {
        for (command in RobotCommandContract.ALL) assertTrue(command, ListenOnly.allows(false, command))
    }

    @Test
    fun `on lets only stop and disable go out`() {
        val allowed = RobotCommandContract.ALL.filter { ListenOnly.allows(true, it) }.toSet()
        assertTrue(allowed == setOf(RobotCommandContract.STOP, RobotCommandContract.DISABLE))
    }

    @Test
    fun `on refuses motion, play, reset, valves, pumps and anything unknown`() {
        for (command in listOf("jog", "jogStart", "jogHeartbeat", "table", "play", "pause", "reset", "valve", "pump", "tool", "speed", "vision", "enable", "unknown")) {
            assertFalse(command, ListenOnly.allows(true, command))
        }
    }
}
