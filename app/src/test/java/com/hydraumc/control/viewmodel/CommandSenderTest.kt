// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - app/src/test/java/com/hydraumc/control/viewmodel/CommandSenderTest.kt
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.viewmodel

import com.hydraumc.control.network.HydraApiException
import com.hydraumc.control.network.RobotCommandContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The consequences of a refused command are decided by the answer and the command, nothing else. */
class CommandSenderTest {
    private fun error(http: Int?, code: String? = null, message: String? = null) =
        HydraApiException("x", httpCode = http, serverCode = code, serverMessage = message)

    private val none = CommandFailure(false, false, false, false, false)

    @Test
    fun aNetworkFailureChangesNothing() {
        assertEquals(none, CommandSender.classify(RobotCommandContract.STOP, error(null)))
    }

    @Test
    fun aRevisionMismatchReadsTheRobotAgain() {
        val f = CommandSender.classify(RobotCommandContract.JOG, error(412))
        assertTrue(f.refreshRobot)
        assertFalse(f.renewSession)
    }

    @Test
    fun anExpiredSessionIsRenewed() {
        assertTrue(CommandSender.classify(RobotCommandContract.JOG, error(401)).renewSession)
        assertTrue(CommandSender.classify(RobotCommandContract.JOG, error(403, "TOKEN_EXPIRED")).renewSession)
    }

    @Test
    fun aJogThatIsNotArmedDisarmsTheScreen() {
        val f = CommandSender.classify(RobotCommandContract.JOG, error(403, "jog-not-armed"))
        assertTrue(f.disarmJog)
        assertFalse(f.renewSession)
    }

    @Test
    fun anUnknownResetIsRememberedOnlyForReset() {
        val unknown = error(400, null, "Unknown command: reset")
        assertTrue(CommandSender.classify(RobotCommandContract.RESET, unknown).homeResetUnsupported)
        assertFalse(CommandSender.classify(RobotCommandContract.JOG, unknown).homeResetUnsupported)
        assertFalse(CommandSender.classify(RobotCommandContract.RESET, unknown).workUnsupported)
    }

    @Test
    fun anUnknownTrajectoryIsRememberedOnlyForTrajectory() {
        val unknown = error(400, null, "unknown command")
        assertTrue(CommandSender.classify(RobotCommandContract.TRAJECTORY, unknown).workUnsupported)
        assertFalse(CommandSender.classify(RobotCommandContract.STOP, unknown).workUnsupported)
    }

    @Test
    fun aBadRequestWithACodeIsNotAnUnknownCommand() {
        assertEquals(none, CommandSender.classify(RobotCommandContract.RESET, error(400, "BAD", "unknown command")))
    }
}
