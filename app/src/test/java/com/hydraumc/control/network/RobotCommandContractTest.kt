// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - app/src/test/java/com/hydraumc/control/network/RobotCommandContractTest.kt
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.network

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** The generated constants agree with docs/robot_command_contract.json (the file the other clients and the checks share). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RobotCommandContractTest {
    private val contract = JSONObject(File("../docs/robot_command_contract.json").readText())

    private fun names(filter: (JSONObject) -> Boolean): Set<String> {
        val commands = contract.getJSONArray("commands")
        return (0 until commands.length()).map { commands.getJSONObject(it) }.filter(filter).map { it.getString("name") }.toSet()
    }

    @Test
    fun everyCommandOfTheContractIsAConstant() {
        assertEquals(names { true }, RobotCommandContract.ALL)
    }

    @Test
    fun theNumberedAndContinuousSetsFollowTheContract() {
        assertEquals(names { it.getBoolean("numbered") }, RobotCommandContract.NUMBERED)
        assertEquals(names { it.getBoolean("continuous") }, RobotCommandContract.CONTINUOUS)
    }

    @Test
    fun theLimitsFollowTheContract() {
        val limits = contract.getJSONObject("limits")
        assertEquals(limits.getDouble("speedMin"), RobotCommandContract.SPEED_MIN, 0.0)
        assertEquals(limits.getDouble("speedMax"), RobotCommandContract.SPEED_MAX, 0.0)
        assertEquals(limits.getDouble("jogMaxAmount"), RobotCommandContract.JOG_MAX_AMOUNT, 0.0)
        assertEquals(limits.getDouble("jogStepMax"), RobotCommandContract.JOG_STEP_MAX, 0.0)
        assertEquals(limits.getInt("outputIndexMax"), RobotCommandContract.OUTPUT_INDEX_MAX)
    }

    @Test
    fun theRetriesAndTheOrderingUseTheContractSets() {
        assertEquals(RobotCommandContract.CONTINUOUS, CONTINUOUS_COMMANDS)
        for (command in RobotCommandContract.NUMBERED) assertEquals(true, CommandSequence.isNumbered(command))
    }
}
