// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - viewmodel/JogArming.kt
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.viewmodel

/**
 * The end of each robot's jog arming as this phone shows it (robot id to a moment on the phone clock). The server clock and the
 * phone clock may differ, so an arming is shown as ending no later than its length allows.
 */
object JogArming {
    /** [armed] after the server said the arming of [robotId] ends at [untilServerMs] (null or past: not armed). */
    fun fromServer(armed: Map<Int, Long>, robotId: Int, untilServerMs: Long?, nowMs: Long, ttlMs: Long): Map<Int, Long> =
        if (untilServerMs != null && untilServerMs > nowMs) armed + (robotId to minOf(untilServerMs, nowMs + ttlMs)) else armed - robotId

    /** [armed] after this phone armed or disarmed [robotId]. */
    fun afterRequest(armed: Map<Int, Long>, robotId: Int, arm: Boolean, nowMs: Long, ttlMs: Long): Map<Int, Long> =
        if (arm) armed + (robotId to nowMs + ttlMs) else armed - robotId

    /** True when the robot's arming has ended or never began. */
    fun needsArming(armed: Map<Int, Long>, robotId: Int, nowMs: Long): Boolean = (armed[robotId] ?: 0L) <= nowMs
}
