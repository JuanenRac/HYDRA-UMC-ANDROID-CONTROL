// =============================================================================
// HYDRA-UMC CONTROL - Health of the link to the server (round-trip time)
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.util

import com.hydraumc.control.network.RobotCommandContract

/**
 * Keeps the last round-trip times measured towards the server and says how good the link is. It holds no network
 * and no clock of its own besides the one it is given, so it is tested on the JVM. The limits come from
 * docs/robot_command_contract.json through the generated RobotCommandContract.
 *
 * The level follows the SLOWEST of the recent samples, not the average: one answer that took half a second
 * is what a held jog would have felt, and an average would hide it.
 */
class LinkHealth(
    private val limits: Limits = Limits(),
    private val clock: () -> Long = System::currentTimeMillis,
) {
    data class Limits(
        val window: Int = RobotCommandContract.LINK_HEALTH_SAMPLE_WINDOW,
        val goodRttMs: Long = RobotCommandContract.LINK_HEALTH_GOOD_RTT_MS,
        val poorRttMs: Long = RobotCommandContract.LINK_HEALTH_POOR_RTT_MS,
        val blockRttMs: Long = RobotCommandContract.LINK_HEALTH_BLOCK_RTT_MS,
        val lostAfterMs: Long = RobotCommandContract.LINK_HEALTH_LOST_AFTER_MS,
    ) {
        init {
            require(window >= 1 && goodRttMs in 1..poorRttMs && poorRttMs <= blockRttMs && lostAfterMs > 0) { "the limits must grow from good to block" }
        }
    }

    enum class Level { UNKNOWN, GOOD, POOR, BAD, LOST }

    private val samples = ArrayDeque<Long>()
    private var lastAnswerAt = 0L
    private var failures = 0

    /** A probe (or any answered request) took [rttMs]. */
    @Synchronized
    fun record(rttMs: Long) {
        samples.addLast(rttMs.coerceAtLeast(0L))
        while (samples.size > limits.window) samples.removeFirst()
        lastAnswerAt = clock()
        failures = 0
    }

    /** A probe got no answer. */
    @Synchronized
    fun recordFailure() {
        failures++
    }

    /** Forgets everything (a new connection). */
    @Synchronized
    fun reset() {
        samples.clear()
        lastAnswerAt = 0L
        failures = 0
    }

    /** The slowest of the recent samples, or null before the first one. */
    @Synchronized
    fun worstMs(): Long? = samples.maxOrNull()

    /** The last sample, or null before the first one. */
    @Synchronized
    fun lastMs(): Long? = samples.lastOrNull()

    @Synchronized
    fun level(): Level {
        if (failures >= 2) return Level.LOST
        if (samples.isEmpty()) return if (failures > 0) Level.LOST else Level.UNKNOWN
        if (clock() - lastAnswerAt > limits.lostAfterMs) return Level.LOST
        val worst = samples.max()
        return when {
            worst < limits.goodRttMs -> Level.GOOD
            worst < limits.poorRttMs -> Level.POOR
            else -> Level.BAD
        }
    }

    /**
     * True when the continuous jog must not start (or must stop): the slowest recent answer reached the blocking time, or the
     * server has not answered for too long. An unknown link (nothing measured yet) does not block by itself.
     */
    @Synchronized
    fun blocksJog(): Boolean {
        val level = level()
        if (level == Level.LOST) return true
        return (samples.maxOrNull() ?: 0L) >= limits.blockRttMs
    }
}
