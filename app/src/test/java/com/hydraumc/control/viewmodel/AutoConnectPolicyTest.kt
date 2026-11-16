// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - app/src/test/java/com/hydraumc/control/viewmodel/AutoConnectPolicyTest.kt
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.viewmodel

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The app connects by itself only to the server it saved. */
class AutoConnectPolicyTest {
    @Test
    fun `the saved server is connected by itself`() {
        assertTrue(AutoConnectPolicy.allows("192.168.1.20" to 3000, "192.168.1.20", 3000))
        assertTrue(AutoConnectPolicy.allows("Hydra.local" to 3000, "hydra.LOCAL", 3000))
    }

    @Test
    fun `another host or another port waits for a tap`() {
        assertFalse(AutoConnectPolicy.allows("192.168.1.20" to 3000, "192.168.1.21", 3000))
        assertFalse(AutoConnectPolicy.allows("192.168.1.20" to 3000, "192.168.1.20", 3001))
    }

    @Test
    fun `the first start with nothing saved connects to nothing`() {
        assertFalse(AutoConnectPolicy.allows(null, "192.168.1.20", 3000))
        assertFalse(AutoConnectPolicy.allows("" to 3000, "192.168.1.20", 3000))
    }
}
