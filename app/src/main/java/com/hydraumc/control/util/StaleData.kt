// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - app/src/main/java/com/hydraumc/control/util/StaleData.kt
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.util

/**
 * When the robot data on screen is a remembered copy and not a live reading.
 * Without a link to the server the cached state looks exactly like live data
 * (robots "online", "playing", positions), so the app shows a warning.
 */
object StaleData {
    /** True when there are robots on screen but no live connection feeds them. */
    fun isStale(connected: Boolean, hasRobots: Boolean): Boolean = hasRobots && !connected
}
