// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - viewmodel/CommandPreflight.kt
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.viewmodel

import com.hydraumc.control.network.JogLimits
import com.hydraumc.control.network.RobotCommandContract
import com.hydraumc.control.network.jogLimitExceeded
import com.hydraumc.control.network.tableLimitExceeded
import org.json.JSONArray
import org.json.JSONObject

/**
 * The checks that run before a robot command changes anything on screen: which robots it reaches and whether a movement is
 * above the limit the server holds. Pure functions, so they are tested without a phone.
 */
object CommandPreflight {
    /** Commands that move the robot and therefore need the jog to be armed and within limits. */
    fun isMotion(command: String): Boolean = command == RobotCommandContract.JOG || command == RobotCommandContract.TABLE

    /** Commands that end a continuous jog held by the operator. */
    fun interruptsHeldJog(command: String): Boolean = command == RobotCommandContract.STOP || command == RobotCommandContract.DISABLE

    /**
     * The limit that [params] of a [command] exceed, or null when it is within [limits] (or the limits are unknown, or the
     * command does not move anything). A table move is checked as a whole, and a relative one also by where it ends.
     */
    fun limitExceeded(command: String, params: JSONObject?, limits: JogLimits?, tableX: Double, tableY: Double): Double? {
        if (!isMotion(command)) return null
        return if (command == RobotCommandContract.TABLE) {
            params?.let { tableLimitExceeded(it, limits, tableX, tableY) }
        } else {
            jogLimitExceeded(
                amount = params?.optDouble("amount", 0.0) ?: 0.0,
                limits = limits,
                absolute = params?.optBoolean("absolute", false) ?: false,
            )
        }
    }

    /** The robot itself, then (when [propagate]) every robot listed in its `combinedWith`. */
    fun affectedIds(robotId: Int, combinedWith: JSONArray?, propagate: Boolean): List<Int> {
        val ids = mutableListOf(robotId)
        if (propagate && combinedWith != null) {
            for (i in 0 until combinedWith.length()) ids.add(combinedWith.getInt(i))
        }
        return ids
    }
}
