// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - app/src/test/java/com/hydraumc/control/viewmodel/CommandPreflightTest.kt
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.viewmodel

import com.hydraumc.control.network.JogLimits
import com.hydraumc.control.network.RobotCommandContract
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The checks before a command changes the screen, and the undo of an edit that the server refused. */
class CommandPreflightTest {
    private val limits = JogLimits(maxAmount = 10.0, maxAbsolute = 500.0)

    @Test
    fun onlyAJogAndATableMoveAreMotion() {
        assertTrue(CommandPreflight.isMotion(RobotCommandContract.JOG))
        assertTrue(CommandPreflight.isMotion(RobotCommandContract.TABLE))
        assertFalse(CommandPreflight.isMotion(RobotCommandContract.STOP))
    }

    @Test
    fun aStopAndASafeModeEndAHeldJog() {
        assertTrue(CommandPreflight.interruptsHeldJog(RobotCommandContract.STOP))
        assertTrue(CommandPreflight.interruptsHeldJog(RobotCommandContract.DISABLE))
        assertFalse(CommandPreflight.interruptsHeldJog(RobotCommandContract.JOG))
    }

    @Test
    fun aRelativeJogIsBoundedByTheAmountLimit() {
        val over = JSONObject().put("amount", 11.0)
        val within = JSONObject().put("amount", -10.0)
        assertEquals(10.0, CommandPreflight.limitExceeded(RobotCommandContract.JOG, over, limits, 0.0, 0.0)!!, 0.0)
        assertNull(CommandPreflight.limitExceeded(RobotCommandContract.JOG, within, limits, 0.0, 0.0))
    }

    @Test
    fun anAbsoluteJogIsBoundedByTheAbsoluteLimit() {
        val params = JSONObject().put("amount", 100.0).put("absolute", true)
        assertNull(CommandPreflight.limitExceeded(RobotCommandContract.JOG, params, limits, 0.0, 0.0))
        params.put("amount", 501.0)
        assertEquals(500.0, CommandPreflight.limitExceeded(RobotCommandContract.JOG, params, limits, 0.0, 0.0)!!, 0.0)
    }

    @Test
    fun unknownLimitsAndOtherCommandsAreLeftToTheServer() {
        val huge = JSONObject().put("amount", 1.0e9)
        assertNull(CommandPreflight.limitExceeded(RobotCommandContract.JOG, huge, null, 0.0, 0.0))
        assertNull(CommandPreflight.limitExceeded(RobotCommandContract.STOP, huge, limits, 0.0, 0.0))
        assertNull(CommandPreflight.limitExceeded(RobotCommandContract.TABLE, null, limits, 0.0, 0.0))
    }

    @Test
    fun aJogWithoutAmountMovesNothing() {
        assertNull(CommandPreflight.limitExceeded(RobotCommandContract.JOG, null, limits, 0.0, 0.0))
    }

    @Test
    fun theAffectedRobotsAreItselfAndItsCombinedOnesOnlyWhenAsked() {
        val combined = JSONArray().put(4).put(7)
        assertEquals(listOf(2, 4, 7), CommandPreflight.affectedIds(2, combined, propagate = true))
        assertEquals(listOf(2), CommandPreflight.affectedIds(2, combined, propagate = false))
        assertEquals(listOf(2), CommandPreflight.affectedIds(2, null, propagate = true))
    }

    @Test
    fun aRefusedEditIsUndoneButANewerDeltaIsKept() {
        val robots = mapOf(
            1 to JSONObject().put("speed", 10).put("pos", JSONObject().put("x", 1)),
            2 to JSONObject().put("speed", 20),
        )
        val edits = OptimisticEdits.apply(listOf(1, 2, 9), { robots[it] }) { id ->
            robots[id]!!.put("speed", 99)
        }
        assertEquals(setOf(1, 2), edits.ids)
        // A delta from the server arrived for robot 2 while the send was waiting.
        robots[2]!!.put("speed", 55)
        edits.rollback { robots[it] }
        assertEquals(10, robots[1]!!.getInt("speed"))
        assertEquals(55, robots[2]!!.getInt("speed"))
    }

    @Test
    fun aRobotThatDisappearedIsSkippedOnRollback() {
        val robots = mutableMapOf(1 to JSONObject().put("speed", 10))
        val edits = OptimisticEdits.apply(listOf(1), { robots[it] }) { robots[it]!!.put("speed", 5) }
        robots.clear()
        edits.rollback { robots[it] }
        assertTrue(robots.isEmpty())
    }

    @Test
    fun anEditThatChangesNothingRecordsNothingToUndo() {
        val robots = mapOf(1 to JSONObject().put("speed", 10))
        val edits = OptimisticEdits.apply(listOf(1), { robots[it] }) { }
        robots[1]!!.put("speed", 12)
        edits.rollback { robots[it] }
        assertEquals(12, robots[1]!!.getInt("speed"))
    }
}
