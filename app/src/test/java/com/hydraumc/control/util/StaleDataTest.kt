// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - app/src/test/java/com/hydraumc/control/util/StaleDataTest.kt
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StaleDataTest {
    @Test
    fun `robots without a live link are stale`() = assertTrue(StaleData.isStale(connected = false, hasRobots = true))

    @Test
    fun `robots with a live link are not stale`() = assertFalse(StaleData.isStale(connected = true, hasRobots = true))

    @Test
    fun `nothing on screen means nothing to warn about`() = assertFalse(StaleData.isStale(connected = false, hasRobots = false))
}
