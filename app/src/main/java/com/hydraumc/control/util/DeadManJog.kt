// =============================================================================
// HYDRA-UMC CONTROL - Continuous jog held by a dead-man: the rules
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.util

import com.hydraumc.control.network.RobotCommandContract

/**
 * The rules of the continuous jog: an axis moves while the control is held, the app sends a heartbeat all the
 * while, and the movement ends as soon as the heartbeat cannot be trusted. This file holds only the rules (no
 * network, no screen): when the app refuses to start, how the speed is bounded and the watchdog that decides the
 * link is gone. Every number comes from docs/robot_command_contract.json through the generated
 * RobotCommandContract, so none is tuned here.
 */
object DeadManJog {
    /** The timings and limits of one session; the defaults are the contract's. */
    data class Settings(
        val heartbeatMs: Long = RobotCommandContract.DEAD_MAN_JOG_HEARTBEAT_MS,
        val serverTimeoutMs: Long = RobotCommandContract.DEAD_MAN_JOG_SERVER_TIMEOUT_MS,
        val maxMissedHeartbeats: Int = RobotCommandContract.DEAD_MAN_JOG_MAX_MISSED_HEARTBEATS,
        val maxHoldMs: Long = RobotCommandContract.DEAD_MAN_JOG_MAX_HOLD_MS,
        val stopAttempts: Int = RobotCommandContract.DEAD_MAN_JOG_STOP_ATTEMPTS,
        val stopRetryPauseMs: Long = RobotCommandContract.DEAD_MAN_JOG_STOP_RETRY_PAUSE_MS,
        val stopTimeoutMs: Long = RobotCommandContract.DEAD_MAN_JOG_STOP_TIMEOUT_MS,
        val velocityMin: Int = RobotCommandContract.DEAD_MAN_JOG_VELOCITY_MIN,
        val velocityMax: Int = RobotCommandContract.DEAD_MAN_JOG_VELOCITY_MAX,
    ) {
        init {
            require(heartbeatMs > 0 && serverTimeoutMs > heartbeatMs) { "the server timeout must be longer than the heartbeat" }
            require(maxMissedHeartbeats >= 1 && maxHoldMs > 0 && stopAttempts >= 1) { "the limits must be positive" }
            require(velocityMin in 1..velocityMax) { "the speed range must be valid" }
        }
    }

    /** Why the app does not start the continuous jog; [NONE] means it may. */
    enum class Block { NONE, OFF, NOT_CONNECTED, OFFLINE, SAFE_MODE, MOVING, STOP_PENDING, NOT_ARMED, ROLE, BAD_LINK, ALREADY_RUNNING, NOT_SUPPORTED }

    /** What the app knows at the moment the control is pressed. */
    data class Situation(
        val switchedOn: Boolean,
        val connected: Boolean,
        val robotOnline: Boolean,
        /** False when the robot is in safe mode (the server refuses everything but STOP then). */
        val robotEnabled: Boolean,
        /** A trajectory is playing (paused or not): a jog would fight its setpoint. */
        val robotPlaying: Boolean,
        /** The global STOP is being sent or has not been confirmed. */
        val stopInProgress: Boolean,
        /** The administrator requires the jog to be armed and it is not. */
        val needsArming: Boolean,
        /** The role of the session (`admin`, `operator`) or null when it cannot be read; the server checks it anyway. */
        val role: String?,
        /** The link health blocks the jog (switch on and the link is bad or lost). */
        val linkBlocks: Boolean,
        /** A continuous jog of this robot is already held. */
        val alreadyRunning: Boolean,
        /** False once the server answered that it does not know the commands. */
        val serverSupports: Boolean,
    )

    fun roleAllowed(role: String?): Boolean = role == null || role == "admin" || role == "operator"

    /** The first reason not to start, in the order a person can fix them; [Block.NONE] when there is none. */
    fun blockOf(s: Situation): Block = when {
        !s.switchedOn -> Block.OFF
        !s.connected -> Block.NOT_CONNECTED
        !s.serverSupports -> Block.NOT_SUPPORTED
        !roleAllowed(s.role) -> Block.ROLE
        !s.robotOnline -> Block.OFFLINE
        !s.robotEnabled -> Block.SAFE_MODE
        s.stopInProgress -> Block.STOP_PENDING
        s.robotPlaying -> Block.MOVING
        s.needsArming -> Block.NOT_ARMED
        s.alreadyRunning -> Block.ALREADY_RUNNING
        s.linkBlocks -> Block.BAD_LINK
        else -> Block.NONE
    }

    /** Why a session ended. */
    enum class EndReason {
        /** The control was released. */
        RELEASED,
        /** Heartbeats failed or went unanswered for too long: the app stopped the axis by itself. */
        LINK_LOST,
        /** The hold passed the longest time allowed. */
        TOO_LONG,
        /** The link health became bad while the control was held. */
        BAD_LINK,
        /** The server refused a heartbeat (the session no longer exists there, the robot is reserved by another...). */
        SERVER_REFUSED,
        /** The app left the screen, lost the connection or a STOP was sent. */
        INTERRUPTED,
    }

    /**
     * The velocity with its magnitude kept inside the range of the contract; the sign is the direction and is kept.
     * A value that is not a number, or zero, becomes the smallest speed forwards.
     */
    fun clampVelocity(velocity: Double, settings: Settings = Settings()): Double {
        if (velocity.isNaN() || velocity == 0.0) return settings.velocityMin.toDouble()
        val magnitude = Math.abs(velocity).coerceIn(settings.velocityMin.toDouble(), settings.velocityMax.toDouble())
        return if (velocity < 0) -magnitude else magnitude
    }

    /**
     * Decides, from the answers to the heartbeats, whether the link can still be trusted. It is fed with the clock
     * of the phone; it never sleeps and sends nothing. The server stops the axis by itself after
     * [Settings.serverTimeoutMs] without a heartbeat, so the app gives the link up before that.
     */
    class Watchdog(private val settings: Settings, private val clock: () -> Long) {
        private var startedAt = clock()
        private var lastAckAt = startedAt
        private var missed = 0

        /** A heartbeat (or the start) was answered. */
        fun acked() {
            lastAckAt = clock()
            missed = 0
        }

        /** A heartbeat failed or timed out. */
        fun missed() {
            missed++
        }

        fun missedInARow(): Int = missed

        /** The reason to stop now, or null while the link can be trusted. */
        fun verdict(): EndReason? {
            val now = clock()
            return when {
                missed >= settings.maxMissedHeartbeats -> EndReason.LINK_LOST
                now - lastAckAt > settings.serverTimeoutMs -> EndReason.LINK_LOST
                now - startedAt > settings.maxHoldMs -> EndReason.TOO_LONG
                else -> null
            }
        }
    }
}
