// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - viewmodel/CommandSender.kt
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.viewmodel

import com.hydraumc.control.network.HydraApiException
import com.hydraumc.control.network.RobotCommandContract
import com.hydraumc.control.network.isUnknownCommand

/**
 * What the app does after the server refuses a robot command, told apart from the sending itself so it can be tested
 * without a phone: which of the screen's memories change and whether the session or the robot must be read again.
 * The screen always goes back to the value before the command (rollback); that is not part of this decision.
 */
data class CommandFailure(
    /** The robot changed since it was read: read it again. */
    val refreshRobot: Boolean,
    /** The server does not know `reset`: remember it, so the button explains instead of sending again. */
    val homeResetUnsupported: Boolean,
    /** The server does not know `trajectory`. */
    val workUnsupported: Boolean,
    /** The jog arming ended: the screen offers to arm again. */
    val disarmJog: Boolean,
    /** A new login or refresh would cure the answer. */
    val renewSession: Boolean,
)

object CommandSender {
    /** The consequences of [error] for [command]. */
    fun classify(command: String, error: HydraApiException): CommandFailure = CommandFailure(
        refreshRobot = error.isRevisionMismatch,
        homeResetUnsupported = command == RobotCommandContract.RESET && isUnknownCommand(error),
        workUnsupported = command == RobotCommandContract.TRAJECTORY && isUnknownCommand(error),
        disarmJog = error.isJogNotArmed,
        renewSession = error.isSessionProblem,
    )
}
