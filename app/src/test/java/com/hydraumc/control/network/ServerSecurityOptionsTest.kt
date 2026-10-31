// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - app/src/test/java/com/hydraumc/control/network/ServerSecurityOptionsTest.kt
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The reading of the access options of the server, the body of a change and the changes that ask first. */
@RunWith(RobolectricTestRunner::class)
class ServerSecurityOptionsTest {

    private val full = """{"port":18000,"aiAccess":{"value":"open","source":"default"},
        "health":{"open":{"value":false,"source":"default"}},
        "safety":{"publishGlobalStop":{"value":false,"source":"config"}},
        "connections":{"recordAddress":{"value":false,"source":"default"}},
        "camera":{"open":{"value":false,"source":"default"}},
        "websocket":{"allowQueryToken":{"value":false,"source":"default"}},
        "logging":{"redactSecrets":{"value":true,"source":"default"}},
        "voice":{"includeHeardText":{"value":false,"source":"default"},"requireSignature":{"value":false,"source":"env"}}}"""

    private fun options() = ServerSecurity.parse(full)!!

    @Test
    fun `all seven options are read with where they come from`() {
        val o = options()
        assertEquals(ServerOption(false, OptionSource.DEFAULT), o.healthOpen)
        assertEquals(ServerOption(false, OptionSource.CONFIG), o.publishGlobalStop)
        assertEquals(ServerOption(true, OptionSource.DEFAULT), o.logRedaction)
        assertTrue(o.voiceSignature!!.locked)
        assertFalse(o.cameraOpen!!.locked)
        assertFalse(o.isEmpty)
    }

    @Test
    fun `a server that predates the options answers without them`() {
        val o = ServerSecurity.parse("""{"port":18000,"aiAccess":{"value":"open","source":"default"}}""")!!
        assertTrue(o.isEmpty)
        val v = o.toValues()
        assertFalse(v.healthOpen)
        assertTrue(v.logRedaction)
        assertNull(ServerSecurity.patch(o, v.copy(healthOpen = true)))
    }

    @Test
    fun `an option with a wrong type or an unknown source is left out and the rest still reads`() {
        val o = ServerSecurity.parse("""{"health":{"open":{"value":"yes","source":"default"}},
            "camera":{"open":{"value":true,"source":"cloud"}},"logging":{"redactSecrets":{"value":false,"source":"config"}}}""")!!
        assertNull(o.healthOpen)
        assertNull(o.cameraOpen)
        assertEquals(ServerOption(false, OptionSource.CONFIG), o.logRedaction)
    }

    @Test
    fun `text that is not an object is not read`() {
        assertNull(ServerSecurity.parse("not json"))
        assertNull(ServerSecurity.parse(""))
    }

    @Test
    fun `the body holds only what changed`() {
        val o = options()
        assertNull(ServerSecurity.patch(o, o.toValues()))
        val body = ServerSecurity.patch(o, o.toValues().copy(healthOpen = true, recordAddress = true))!!
        assertEquals(setOf("health", "connections"), body.keys().asSequence().toSet())
        assertEquals(true, body.getJSONObject("health").getBoolean("open"))
        assertEquals(true, body.getJSONObject("connections").getBoolean("recordAddress"))
    }

    @Test
    fun `each option goes under the group the server reads it from`() {
        val o = options()
        val body = ServerSecurity.patch(o, ServerSecurityValues(
            healthOpen = true, publishGlobalStop = true, recordAddress = true, cameraOpen = true,
            wsQueryToken = true, logRedaction = false, voiceSignature = true))!!
        assertTrue(body.getJSONObject("safety").getBoolean("publishGlobalStop"))
        assertTrue(body.getJSONObject("camera").getBoolean("open"))
        assertTrue(body.getJSONObject("websocket").getBoolean("allowQueryToken"))
        assertFalse(body.getJSONObject("logging").getBoolean("redactSecrets"))
        // the signature is fixed by the environment of the server: it is never sent
        assertFalse(body.has("voice"))
    }

    @Test
    fun `a value fixed by the environment is neither sent nor asked about`() {
        val o = options()
        val next = o.toValues().copy(voiceSignature = false)
        assertNull(ServerSecurity.patch(o, next))
        val fixedOn = ServerSecurity.parse(
            """{"voice":{"requireSignature":{"value":true,"source":"env"}}}""")!!
        assertTrue(ServerSecurity.lowerings(fixedOn, fixedOn.toValues().copy(voiceSignature = false)).isEmpty())
        assertNull(ServerSecurity.patch(fixedOn, fixedOn.toValues().copy(voiceSignature = false)))
    }

    @Test
    fun `loosening a switch asks and tightening it or announcing a stop does not`() {
        val o = options()
        val v = o.toValues()
        assertEquals(listOf(ProtectionLowering.HEALTH_OPEN), ServerSecurity.lowerings(o, v.copy(healthOpen = true)))
        assertEquals(listOf(ProtectionLowering.RECORD_ADDRESS), ServerSecurity.lowerings(o, v.copy(recordAddress = true)))
        assertEquals(listOf(ProtectionLowering.CAMERA_OPEN), ServerSecurity.lowerings(o, v.copy(cameraOpen = true)))
        assertEquals(listOf(ProtectionLowering.WS_QUERY_TOKEN), ServerSecurity.lowerings(o, v.copy(wsQueryToken = true)))
        assertEquals(listOf(ProtectionLowering.LOG_REDACTION_OFF), ServerSecurity.lowerings(o, v.copy(logRedaction = false)))
        assertTrue(ServerSecurity.lowerings(o, v.copy(publishGlobalStop = true)).isEmpty())

        val open = ServerSecurity.parse(
            """{"health":{"open":{"value":true,"source":"config"}},"camera":{"open":{"value":true,"source":"config"}}}""")!!
        assertTrue(ServerSecurity.lowerings(open, open.toValues().copy(healthOpen = false, cameraOpen = false)).isEmpty())
        assertNotNull(ServerSecurity.patch(open, open.toValues().copy(healthOpen = false)))
    }

    @Test
    fun `several loosenings are listed in a fixed order`() {
        val o = options()
        val all = o.toValues().copy(healthOpen = true, recordAddress = true, cameraOpen = true, wsQueryToken = true, logRedaction = false)
        assertEquals(
            listOf(ProtectionLowering.HEALTH_OPEN, ProtectionLowering.RECORD_ADDRESS, ProtectionLowering.CAMERA_OPEN,
                ProtectionLowering.WS_QUERY_TOKEN, ProtectionLowering.LOG_REDACTION_OFF),
            ServerSecurity.lowerings(o, all))
    }
}
