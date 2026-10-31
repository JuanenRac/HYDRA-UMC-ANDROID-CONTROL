// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - app/src/test/java/com/hydraumc/control/network/ConnectionHistoryTest.kt
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The reading of the list of clients and of the history of sign-ins that the server gives. */
@RunWith(RobolectricTestRunner::class)
class ConnectionHistoryTest {

    private val client = """{"schema_version":"1.0","id":"0123456789abcdef","kind":"android","user":"ana","app_version":"0.7.4","origin":"local",
        "two_factor":true,"first_seen_utc":"2026-01-01T00:00:00.000Z","last_seen_utc":"2026-01-02T00:00:00.000Z","active_sessions":2,"logins_ok":5,"logins_failed":1}"""

    @Test
    fun `a date is read as the server writes it`() {
        assertEquals(1_767_225_600_000L, ConnectionHistory.millis("2026-01-01T00:00:00.000Z"))
        assertEquals(1_767_225_600_123L, ConnectionHistory.millis("2026-01-01T00:00:00.123Z"))
        assertEquals(1_767_225_601_000L, ConnectionHistory.millis("2026-01-01T00:00:01Z"))
        assertNull(ConnectionHistory.millis("yesterday"))
        assertNull(ConnectionHistory.millis("2026-13-01T00:00:00.000Z"))
        assertNull(ConnectionHistory.millis(null))
    }

    @Test
    fun `a client is read field by field`() {
        val list = ConnectionHistory.parseClients("""{"clients":[$client]}""")
        assertEquals(1, list.size)
        val c = list[0]
        assertEquals("android", c.kind)
        assertEquals("ana", c.user)
        assertEquals("0.7.4", c.appVersion)
        assertEquals("local", c.origin)
        assertEquals(true, c.twoFactor)
        assertEquals(2, c.activeSessions)
        assertEquals(5, c.loginsOk)
        assertEquals(1, c.loginsFailed)
        assertNull(c.ip)
    }

    @Test
    fun `a kind it does not know is other and an entry with a bad identifier or date is left out`() {
        val odd = client.replace("\"kind\":\"android\"", "\"kind\":\"toaster\"")
        assertEquals("other", ConnectionHistory.parseClients("""{"clients":[$odd]}""")[0].kind)
        assertTrue(ConnectionHistory.parseClients("""{"clients":[${client.replace("0123456789abcdef", "nope")}]}""").isEmpty())
        assertTrue(ConnectionHistory.parseClients("""{"clients":[${client.replace("2026-01-02T00:00:00.000Z", "later")}]}""").isEmpty())
        assertTrue(ConnectionHistory.parseClients("""{"clients":[5,null,{}]}""").isEmpty())
        assertTrue(ConnectionHistory.parseClients("""{}""").isEmpty())
    }

    @Test
    fun `the most recently seen client comes first and an address is kept only when it is sent`() {
        val older = client.replace("0123456789abcdef", "fedcba9876543210").replace("2026-01-02T00:00:00.000Z", "2026-01-01T12:00:00.000Z")
            .replace("\"active_sessions\":2", "\"active_sessions\":0,\"ip\":\"192.168.0.9\"")
        val list = ConnectionHistory.parseClients("""{"clients":[$older,$client]}""")
        assertEquals(listOf("0123456789abcdef", "fedcba9876543210"), list.map { it.id })
        assertEquals("192.168.0.9", list[1].ip)
        assertNull(list[0].ip)
    }

    @Test
    fun `events are read newest first and only the actions the server writes`() {
        val events = """{"events":[
            {"schema_version":"1.0","id":1,"at_utc":"2026-01-01T00:00:00.000Z","action":"login","client_kind":"studio","client_id":"0123456789abcdef","user":"ana"},
            {"schema_version":"1.0","id":3,"at_utc":"2026-01-03T00:00:00.000Z","action":"login_failed","client_kind":"android","client_id":"0123456789abcdef","user":"ana","app_version":"0.7.4"},
            {"schema_version":"1.0","id":2,"at_utc":"2026-01-02T00:00:00.000Z","action":"format_disk","client_kind":"studio","client_id":"0123456789abcdef"},
            {"schema_version":"1.0","id":0,"at_utc":"2026-01-02T00:00:00.000Z","action":"login","client_kind":"studio","client_id":"0123456789abcdef"}]}"""
        val list = ConnectionHistory.parseEvents(events)
        assertEquals(listOf(3L, 1L), list.map { it.id })
        assertTrue(list[0].isFailure)
        assertFalse(list[1].isFailure)
        assertEquals("0.7.4", list[0].appVersion)
        assertNull(list[1].appVersion)
    }

    @Test
    fun `how long ago is short`() {
        val now = 10_000_000_000L
        assertEquals(30L to "s", ConnectionHistory.ago(now - 30_000, now))
        assertEquals(12L to "min", ConnectionHistory.ago(now - 12 * 60_000, now))
        assertEquals(3L to "h", ConnectionHistory.ago(now - 3 * 3_600_000, now))
        assertEquals(3L to "d", ConnectionHistory.ago(now - 3 * 86_400_000, now))
        assertEquals(0L to "s", ConnectionHistory.ago(now + 5_000, now))
    }
}
