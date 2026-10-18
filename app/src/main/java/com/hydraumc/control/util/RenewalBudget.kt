// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - util/RenewalBudget.kt
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.util

/**
 * How often the app may renew the session by itself. A renewal that is followed by another "session rejected" a moment later is not curing anything: at least
 * [minGapMs] must pass after one renewal before the next, and no more than [maxPerWindow] fit in [windowMs]. Past that the app goes back to the sign-in
 * instead of looping (every loop used to sign in again and ended by tripping the server's login rate limit).
 */
class RenewalBudget(
    private val minGapMs: Long = 20_000L,
    private val windowMs: Long = 10 * 60_000L,
    private val maxPerWindow: Int = 3,
    private val clockMs: () -> Long = { System.currentTimeMillis() },
) {
    private val times = ArrayDeque<Long>()

    /** True (and the renewal is counted) when another renewal is allowed now; false when the app must stop trying. */
    @Synchronized
    fun tryAcquire(): Boolean {
        val now = clockMs()
        while (times.isNotEmpty() && now - times.first() > windowMs) times.removeFirst()
        if (times.size >= maxPerWindow || (times.isNotEmpty() && now - times.last() < minGapMs)) return false
        times.addLast(now)
        return true
    }

    /** A sign-in typed by the person starts the count again. */
    @Synchronized
    fun reset() { times.clear() }
}
