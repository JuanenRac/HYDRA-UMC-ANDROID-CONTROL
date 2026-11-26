// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - WatchReleaseNoticeRequestTest.kt
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.network

import hydra.umc.client.HydraPostWatchReleaseNoticeBody
import org.junit.Assert.assertEquals
import org.junit.Test

/** What the phone sends about the watch notice has the names of the server contract. */
class WatchReleaseNoticeRequestTest {
    @Test
    fun theBodyHasTheNamesOfTheContract() {
        val body = HydraPostWatchReleaseNoticeBody("phone-1", "android", false).toJson()
        assertEquals(setOf("device_id", "platform", "notifies_watch"), body.keys)
        assertEquals(false, body["notifies_watch"])
    }
}
