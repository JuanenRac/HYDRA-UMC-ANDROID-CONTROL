// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - app/src/test/java/com/hydraumc/control/wear/WatchStopRestoredPushTest.kt
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.wear

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** What the phone passes to the watch about a stop restored after a restart of the server: the message, and when. */
@RunWith(RobolectricTestRunner::class)
class WatchStopRestoredPushTest {
    @Test
    fun `the message carries the flag in the shape the watch accepts`() {
        val json = WatchStopRestored(true).toJson()
        assertEquals("stop_restored_status", json.getString("type"))
        assertEquals(1, json.getInt("protocolVersion"))
        assertEquals(true, json.getBoolean("restored"))
    }

    @Test
    fun `another protocol version is refused`() {
        try {
            WatchStopRestored(true, protocolVersion = 2)
            fail("accepted another version")
        } catch (_: IllegalArgumentException) {
            // refused
        }
    }

    @Test
    fun `a value is sent when it changes and a repeat is not`() {
        val policy = StopRestoredWatchPolicy()
        assertTrue(policy.accept(true))
        assertFalse(policy.accept(true))
        assertTrue(policy.toldRestored)
        assertTrue(policy.accept(false))
        assertFalse(policy.toldRestored)
        policy.reset()
        assertTrue("after a reset the next value is sent whatever it is", policy.accept(false))
    }
}
