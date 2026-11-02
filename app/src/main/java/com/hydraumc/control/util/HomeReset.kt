// =============================================================================
// HYDRA-UMC CONTROL - HOME/RESET: when it is offered and how it is confirmed
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.util

/**
 * HOME/RESET takes the selected robot to its zero position through the server's `reset` command. It is a
 * movement of a real robot, so this file holds only the rules (no network, no screen): when the app refuses
 * to offer it, and the confirmation in steps that must all be given, in order, within a time window.
 *
 * The numbers (switch off by default, steps, window) come from docs/robot_command_contract.json through
 * the generated RobotCommandContract, so they are not tuned here.
 */
object HomeReset {
    /** Why the app does not send the reset; [NONE] means it may go on to the confirmation. */
    enum class Block { NONE, OFF, NOT_CONNECTED, OFFLINE, SAFE_MODE, MOVING, JOG_BUSY, STOP_PENDING, NOT_ARMED, ROLE, NO_POSE, NOT_SUPPORTED }

    /** What the app knows about the robot and the session at the moment of the request. */
    data class Situation(
        val switchedOn: Boolean,
        val connected: Boolean,
        val robotOnline: Boolean,
        /** False when the robot is in safe mode (the server refuses everything but STOP then). */
        val robotEnabled: Boolean,
        /** A trajectory is playing (paused or not): a reset would fight its setpoint. */
        val robotPlaying: Boolean,
        val jogInFlight: Boolean,
        /** The global STOP is being sent or has not been confirmed. */
        val stopInProgress: Boolean,
        /** The administrator requires the jog to be armed and it is not. */
        val needsArming: Boolean,
        /** The role in the session token (`admin`, `operator`) or null when it cannot be read; the server checks it anyway. */
        val role: String?,
        /** The robot has a pose to take to zero. */
        val hasPose: Boolean,
        /** False once the server answered that it does not know the command. */
        val serverSupports: Boolean,
    )

    fun roleAllowed(role: String?): Boolean = role == null || role == "admin" || role == "operator"

    /** The first reason not to send the reset, in the order a person can fix them; [Block.NONE] when there is none. */
    fun blockOf(s: Situation): Block = when {
        !s.switchedOn -> Block.OFF
        !s.connected -> Block.NOT_CONNECTED
        !s.serverSupports -> Block.NOT_SUPPORTED
        !roleAllowed(s.role) -> Block.ROLE
        !s.robotOnline -> Block.OFFLINE
        !s.robotEnabled -> Block.SAFE_MODE
        s.stopInProgress -> Block.STOP_PENDING
        s.robotPlaying -> Block.MOVING
        s.jogInFlight -> Block.JOG_BUSY
        s.needsArming -> Block.NOT_ARMED
        !s.hasPose -> Block.NO_POSE
        else -> Block.NONE
    }

    /**
     * The confirmation in [steps] steps. [advance] returns the number of the step just accepted; the last one
     * reports [Progress.Done]. A step given after the window has passed starts over, and so does [cancel].
     */
    class Confirmation(
        private val steps: Int,
        private val windowMs: Long,
        private val clock: () -> Long = System::currentTimeMillis,
    ) {
        sealed class Progress {
            /** [next] is the number (1-based) of the step the person has to give now. */
            data class Waiting(val next: Int) : Progress()
            object Done : Progress()
        }

        private var accepted = 0
        private var startedAt = 0L

        /** The number of the step to show now (1-based); 1 when nothing is under way or the window has passed. */
        fun current(): Int = if (expired()) 1 else accepted + 1

        private fun expired(): Boolean = accepted > 0 && clock() - startedAt > windowMs

        @Synchronized
        fun advance(): Progress {
            if (expired()) accepted = 0
            if (accepted == 0) startedAt = clock()
            accepted += 1
            return if (accepted >= steps.coerceAtLeast(1)) {
                accepted = 0
                Progress.Done
            } else {
                Progress.Waiting(accepted + 1)
            }
        }

        @Synchronized
        fun cancel() {
            accepted = 0
        }
    }
}
