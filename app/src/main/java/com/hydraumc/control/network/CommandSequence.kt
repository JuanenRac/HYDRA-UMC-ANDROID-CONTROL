// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - network/CommandSequence.kt
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.network

/**
 * The number this phone puts on its motion commands (`seq`). The server refuses a jog whose number is not above
 * the highest one it has seen from the same account and controller, and a stop raises that mark, so a jog sent
 * before a stop can never move the robot after it. The number grows by at least one with every command and
 * follows the clock, so a restarted app starts above everything an earlier run sent.
 */
object CommandSequence {
    /** The commands the server orders by number: the motion commands and the stop that must overtake them. */
    private val NUMBERED = setOf("jog", "table", "joints", "stop")

    private var last = 0L

    fun isNumbered(command: String): Boolean = command in NUMBERED

    /** The next number: above the previous one and not below the clock. */
    @Synchronized
    fun next(clock: () -> Long = System::currentTimeMillis): Long {
        last = maxOf(last + 1, clock())
        return last
    }

    /** Starts over (tests only). */
    @Synchronized
    internal fun reset() {
        last = 0L
    }
}
