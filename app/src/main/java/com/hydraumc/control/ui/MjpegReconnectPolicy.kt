// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - app/src/main/java/com/hydraumc/control/ui/MjpegReconnectPolicy.kt
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.ui

/**
 * How long the camera player waits before it opens a stream again.
 *
 * The pause starts at one second, doubles with every attempt that brought no
 * frame, stops growing after four doublings and never passes the ceiling, so a
 * dead camera is polled gently and a recovered one is picked up within seconds.
 * Pure on purpose: the schedule is tested without a screen or a socket.
 */
object MjpegReconnectPolicy {
    const val BASE_DELAY_MS = 1_000L
    const val MAX_DELAY_MS = 15_000L
    private const val MAX_DOUBLINGS = 4
    private const val COUNT_LIMIT = 1_000

    /** Attempts in a row that brought no frame after the one that just ended. */
    fun failuresAfter(previousFailures: Int, gotFrame: Boolean): Int =
        if (gotFrame) 0 else previousFailures.coerceIn(0, COUNT_LIMIT) + 1

    /** Pause before the next attempt, for the number of failures in a row. */
    fun delayMs(failures: Int): Long =
        (BASE_DELAY_MS shl failures.coerceIn(0, MAX_DOUBLINGS)).coerceAtMost(MAX_DELAY_MS)
}
