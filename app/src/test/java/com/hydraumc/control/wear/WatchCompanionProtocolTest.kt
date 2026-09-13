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
        assertEquals("watch-voice-001", WatchVoiceTurn.fromJson(payload).requestId)
    }

    @Test
    fun `paired relay replies preserve the safe response metadata`() {
        val original = WatchAssistantReply(
            requestId = "watch-voice-003",
            text = "Mission requests require confirmation.",
            level = "ATTENTION",
            speak = true,
            requiresConfirmation = true,
        )

        assertEquals(original, WatchAssistantReply.fromJson(original.toJson()))
    }

    // H062: a reply with no errorCode at all (every real AI reply, whose
    // own `text` is already correctly localized upstream) must round-trip
    // with errorCode staying null - no field magically appears in the wire
    // JSON, and none is required to parse a message that never had one.
    @Test
    fun `assistant reply with no errorCode omits the field from the wire JSON`() {
        val original = WatchAssistantReply(
            requestId = "watch-voice-004",
            text = "Robot 3 is online and idle.",
            level = "NOMINAL",
            speak = true,
            requiresConfirmation = false,
        )
        val json = original.toJson()

        assertTrue(!json.has("errorCode"))
        assertEquals(original, WatchAssistantReply.fromJson(json))
        assertEquals(null, WatchAssistantReply.fromJson(json).errorCode)
    }

    // The real H062 scenario: WatchVoiceRelayService's own connection-
    // unavailable fallback must carry the stable errorCode the watch
    // resolves to a real localized string, alongside the English text
    // kept only as a fallback for an old watch build.
    @Test
    fun `assistant reply with a real errorCode round-trips it exactly`() {
        val original = WatchAssistantReply(
            requestId = "watch-voice-005",
            text = "HYDRA-UMC connection unavailable. Check the paired phone session.",
            level = "ATTENTION",
            speak = true,
            requiresConfirmation = false,
            errorCode = "connection_unavailable",
        )
        val json = original.toJson()

        assertEquals("connection_unavailable", json.getString("errorCode"))
        assertEquals(original, WatchAssistantReply.fromJson(json))
    }

    @Test
    fun `system status with a real errorCode round-trips it exactly`() {
        val original = WatchSystemStatus(
            headline = "HYDRA-UMC offline",
            detail = "Check the paired phone connection and Server session.",
            level = "OFFLINE",
            speak = false,
            errorCode = "offline",
        )
        val json = original.toJson()

        assertEquals("offline", json.getString("errorCode"))
        assertEquals(original, WatchSystemStatus.fromJson(json))
    }

    @Test
    fun `system status with no errorCode omits the field from the wire JSON`() {
        val original = WatchSystemStatus(
            headline = "All systems nominal",
            detail = "8 robots online, 0 alerts.",
            level = "NOMINAL",
            speak = false,
        )
        val json = original.toJson()

        assertTrue(!json.has("errorCode"))
        assertEquals(null, WatchSystemStatus.fromJson(json).errorCode)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `voice turn rejects an oversized transcript`() {
        WatchVoiceTurn("watch-voice-002", "x".repeat(501), "en-US")
    }
}
