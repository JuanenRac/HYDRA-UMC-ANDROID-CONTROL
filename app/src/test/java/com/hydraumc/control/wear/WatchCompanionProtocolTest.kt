// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - Watch companion protocol unit tests
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0 - see LICENSE
// =============================================================================
package com.hydraumc.control.wear

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WatchCompanionProtocolTest {
    @Test
    fun `version status uses the exact contract expected by Watch`() {
        val payload = JSONObject(
            WatchCompanionVersionStatus(
                appVersion = "0.2.9",
                updateAvailable = true,
            ).toWireJson(),
        )

        assertEquals("companion_version_status", payload.getString("type"))
        assertEquals(1, payload.getInt("protocolVersion"))
        assertEquals("0.2.9", payload.getString("appVersion"))
        assertTrue(payload.getBoolean("updateAvailable"))
    }

    @Test
    fun `voice turn uses the bounded Watch to Server contract`() {
        val payload = WatchVoiceTurn(
            requestId = "watch-voice-001",
            transcript = "status for robot A1",
            locale = "en-US",
        ).toJson()

        assertEquals("voice_turn", payload.getString("type"))
        assertEquals("watch-voice-001", payload.getString("requestId"))
        assertEquals("status for robot A1", payload.getString("transcript"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `voice turn rejects an oversized transcript`() {
        WatchVoiceTurn("watch-voice-002", "x".repeat(501), "en-US")
    }
}
