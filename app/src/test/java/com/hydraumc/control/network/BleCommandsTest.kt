// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - app/src/test/java/com/hydraumc/control/network/BleCommandsTest.kt
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.network

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The command message and the acknowledgement (org.json needs the Robolectric runner). */
@RunWith(RobolectricTestRunner::class)
class BleCommandsTest {
    @Test fun `the command message carries every field of the contract`() {
        val request = BleCommandRequest(2, "jog", JSONObject().put("axis", "x").put("amount", 1.5), "phone-1", "tok")
        val body = JSONObject(String(BleCommands.encode("c-1", request), Charsets.UTF_8))
        assertEquals("c-1", body.getString("id"))
        assertEquals(2, body.getInt("robotId"))
        assertEquals("jog", body.getString("command"))
        assertEquals("x", body.getJSONObject("params").getString("axis"))
        assertEquals("tok", body.getString("token"))
        assertEquals("phone-1", body.getString("controllerId"))
    }

    @Test fun `a command without a controller id does not send an empty one`() {
        val body = JSONObject(String(BleCommands.encode("c-1", BleCommandRequest(1, "stop", token = "t")), Charsets.UTF_8))
        assertFalse(body.has("controllerId"))
        assertEquals(0, body.getJSONObject("params").length())
    }

    @Test fun `an acknowledgement is read with its status, code and error`() {
        val ack = BleCommands.parseAck("""{"id":"c-1","ok":false,"status":409,"code":"claimed","error":"reserved by ana"}""".toByteArray())!!
        assertEquals(BleAck("c-1", false, 409, "claimed", "reserved by ana"), ack)
        val ok = BleCommands.parseAck("""{"id":"c-2","ok":true,"status":200,"code":"ok"}""".toByteArray())!!
        assertTrue(ok.ok)
        assertNull(ok.error)
    }

    @Test fun `an acknowledgement with no readable id keeps a null id`() {
        val ack = BleCommands.parseAck("""{"id":null,"ok":false,"status":0,"code":"damaged-message"}""".toByteArray())!!
        assertNull(ack.id)
        assertEquals("damaged-message", ack.code)
        assertNull(BleCommands.parseAck("""{"ok":false,"code":"x"}""".toByteArray())!!.id)
    }

    @Test fun `what is not an acknowledgement is none, never an exception`() {
        for (junk in listOf("", "not json", "[]", "{}", """{"ok":"yes"}""", """{"id":"a"}""", "\u0000\u0001")) {
            assertNull(junk, BleCommands.parseAck(junk.toByteArray()))
        }
        assertNull(BleCommands.parseAck(byteArrayOf(0xFF.toByte(), 0xFE.toByte(), 0x00)))
    }

    @Test fun `a status or code of the wrong type falls back instead of failing`() {
        val ack = BleCommands.parseAck("""{"id":"a","ok":true,"status":"200","code":7}""".toByteArray())!!
        assertEquals(0, ack.status)
        assertEquals("", ack.code)
    }
}
