// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - app/src/test/java/com/hydraumc/control/network/SavedServersTest.kt
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.network

import com.hydraumc.control.MemoryKv
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The list of kept servers and the parking of one session for each of them. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SavedServersTest {

    private val a = "10.0.0.1:3000"
    private val b = "10.0.0.2:3000"

    @Test
    fun theListSurvivesItsOwnTextWithTheTlsSettingsOfEachServer() {
        val pinned = SavedServer("10.0.0.1", 3000, "Workshop", TlsSettings(TlsMode.REQUIRED, "ab".repeat(32), "", allowPlainRemote = true))
        val plain = SavedServer("10.0.0.2", 3000, "10.0.0.2")
        val read = SavedServerList.decode(SavedServerList.encode(listOf(pinned, plain)))
        assertEquals(listOf(pinned, plain), read)
        assertEquals("Workshop (10.0.0.1:3000)", read[0].label)
        assertEquals("10.0.0.2:3000", read[1].label)
    }

    @Test
    fun aDamagedEntryIsDroppedAndAnUnknownTlsModeNeverBecomesAWeakerOne() {
        assertTrue(SavedServerList.decode("not json").isEmpty())
        val read = SavedServerList.decode("""[{"host":"","port":3000},{"host":"x","port":0},{"host":"cell","port":3000,"tls":"SOMETHING"}]""")
        assertEquals(1, read.size)
        assertEquals(TlsMode.REQUIRED, read[0].tls.mode)
        assertFalse(read[0].tls.allowPlainRemote)
    }

    @Test
    fun theSameAddressIsOneEntryAndTheListIsBounded() {
        var list = emptyList<SavedServer>()
        repeat(SavedServerList.MAX + 3) { list = SavedServerList.upsert(list, SavedServer("host$it", 3000, "n$it")) }
        assertEquals(SavedServerList.MAX, list.size)
        val first = list.first()
        list = SavedServerList.upsert(list, first.copy(name = "renamed"))
        assertEquals(SavedServerList.MAX, list.size)
        assertEquals("renamed", list.first().name)
        assertTrue(SavedServerList.remove(list, first.id).none { it.id == first.id })
    }

    private fun MemoryKv.session(server: String, token: String) = write {
        putString(ServerSessions.SERVER, server)
        putString("username", "user-of-$token")
        putString("password", "pw-of-$token")
        putString("token", token)
        putString("refresh_token", "refresh-$token")
        putBoolean("remember_me", true)
        putBoolean("is_logged_in", true)
    }

    @Test
    fun switchingParksOneSessionAndNeverHandsItToTheOtherServer() {
        val kv = MemoryKv()
        kv.session(a, "token-a")

        ServerSessions.switchTo(kv, b)
        assertEquals(b, kv.getString(ServerSessions.SERVER))
        for (key in ServerSessions.TEXT_KEYS) assertNull("a new server starts with nothing: $key", kv.getString(key))
        assertFalse(kv.getBoolean("remember_me", false))

        kv.session(b, "token-b")
        ServerSessions.switchTo(kv, a)
        assertEquals("token-a", kv.getString("token"))
        assertEquals("refresh-token-a", kv.getString("refresh_token"))
        assertEquals("pw-of-token-a", kv.getString("password"))
        assertTrue(kv.getBoolean("remember_me", false))

        ServerSessions.switchTo(kv, b)
        assertEquals("token-b", kv.getString("token"))
        assertEquals("pw-of-token-b", kv.getString("password"))
    }

    @Test
    fun aForgottenServerLeavesNothingBehind() {
        val kv = MemoryKv()
        kv.session(a, "token-a")
        ServerSessions.switchTo(kv, b)
        ServerSessions.forget(kv, a)
        assertTrue(kv.values.keys.none { it.contains(a) })
        ServerSessions.switchTo(kv, a)
        assertNull(kv.getString("token"))
    }

    @Test
    fun aSealedPasswordOfAParkedServerIsSeen() {
        val kv = MemoryKv()
        kv.session(a, "token-a")
        kv.write { putString("sealed_password", "sealed") }
        assertFalse(ServerSessions.anySealed(kv, listOf(a)))
        ServerSessions.switchTo(kv, b)
        assertTrue(ServerSessions.anySealed(kv, listOf(a)))
    }
}
