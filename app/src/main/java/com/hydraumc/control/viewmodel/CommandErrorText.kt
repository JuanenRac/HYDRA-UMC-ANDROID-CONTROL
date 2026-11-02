// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - viewmodel/CommandErrorText.kt
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.viewmodel

import android.content.Context
import com.hydraumc.control.R
import com.hydraumc.control.network.HydraApiException
import com.hydraumc.control.network.RobotCommandContract
import com.hydraumc.control.network.isUnknownCommand

/** The texts the screen shows when a command fails, in the language of the phone. */
object CommandErrorText {
    /** The message for a command the server answered with an error, by what the answer says. */
    fun describe(context: Context, command: String, e: HydraApiException): String = when {
        e.isSessionProblem -> context.getString(R.string.error_session_expired)
        command == RobotCommandContract.RESET && isUnknownCommand(e) -> context.getString(R.string.home_reset_not_supported)
        command == RobotCommandContract.RESET && e.isPermissionDenied -> context.getString(R.string.home_reset_blocked_role)
        e.isRobotDisabled -> context.getString(R.string.error_robot_disabled, command)
        e.isRevisionMismatch -> context.getString(R.string.error_revision_changed, command)
        e.isJogNotArmed -> context.getString(R.string.error_jog_not_armed)
        e.isStaleSequence -> context.getString(R.string.error_stale_sequence, command)
        e.isHomeRejected -> context.getString(R.string.error_home_rejected, command)
        e.isOtherController -> context.getString(R.string.error_claim_other_controller, e.serverMessage ?: "")
        e.isControllerIdRequired -> context.getString(R.string.error_claim_controller_required, e.serverMessage ?: "")
        e.isReservedByAnother -> context.getString(R.string.error_robot_reserved, e.serverMessage ?: "")
        e.isPermissionDenied -> context.getString(R.string.error_permission_denied)
        e.isRejectedCommand -> context.getString(R.string.error_command_rejected, command, e.serverMessage ?: "")
        command == RobotCommandContract.STOP -> context.getString(R.string.error_stop_not_confirmed)
        else -> context.getString(R.string.error_command_failed, command)
    }

    /** `1000.0` is shown as `1000`, `12.5` as `12.5`. */
    fun formatLimit(limit: Double): String = limit.toString().removeSuffix(".0")
}
