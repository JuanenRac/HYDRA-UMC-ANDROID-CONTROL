// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - app/src/test/java/com/hydraumc/control/network/ListenOnlyServersTest.kt
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Listen only for one server: the list is empty by default, it applies to the server in use alone, and a damaged list is cleaned. */
class ListenOnlyServersTest {
    @Test
    fun `a server is named by its host and port`() {
        assertEquals("10.0.0.5:3000", ListenOnly.serverKey("10.0.0.5", 3000))
    }

    @Test
    fun `nothing is read-only by default`() {
        assertFalse(ListenOnly.effective(false, emptySet(), "10.0.0.5:3000"))
        assertFalse(ListenOnly.effective(false, emptySet(), null))
    }

    @Test
    fun `the listed server is read-only and another is not`() {
        val servers = setOf("10.0.0.5:3000")
        assertTrue(ListenOnly.effective(false, servers, "10.0.0.5:3000"))
        assertFalse(ListenOnly.effective(false, servers, "10.0.0.6:3000"))
        assertFalse(ListenOnly.effective(false, servers, null))
    }

    @Test
    fun `the general switch covers every server`() {
        assertTrue(ListenOnly.effective(true, emptySet(), "10.0.0.6:3000"))
        assertTrue(ListenOnly.effective(true, emptySet(), null))
    }

    @Test
    fun `a stored list is cleaned and bounded`() {
        val stored = listOf("ok:1", "", " x", "y".repeat(201), "ok:1") + (1..80).map { "h$it:1" }
        val clean = ListenOnly.cleanServers(stored)
        assertTrue("ok:1" in clean)
        assertFalse("" in clean)
        assertFalse(" x" in clean)
        assertTrue(clean.size <= ListenOnly.MAX_SERVERS)
        assertTrue(clean.all { it.length <= 200 && it.trim() == it })
    }

    @Test
    fun `the stop and the disable still go out to a read-only server`() {
        val on = ListenOnly.effective(false, setOf("a:1"), "a:1")
        assertTrue(ListenOnly.allows(on, RobotCommandContract.STOP))
        assertTrue(ListenOnly.allows(on, RobotCommandContract.DISABLE))
        assertFalse(ListenOnly.allows(on, "jog"))
    }
}
