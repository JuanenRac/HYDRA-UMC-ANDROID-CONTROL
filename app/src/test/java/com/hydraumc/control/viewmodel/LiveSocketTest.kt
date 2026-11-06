// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - test/viewmodel/LiveSocketTest.kt
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.viewmodel

import com.hydraumc.control.network.WsStatus
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The socket owner: one socket at a time, and the signals of the server being left are ignored during a switch. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LiveSocketTest {
    private class FakeSocket(val target: SocketTarget, val callbacks: SocketCallbacks) : SocketHandle {
        var connected = false
        var disconnects = 0
        override fun connect() { connected = true }
        override fun disconnect() { disconnects++ }
    }

    private class Recorder : SocketListener {
        val events = mutableListOf<String>()
        override fun onStatus(status: WsStatus) { events += "status:$status" }
        override fun onClosedIdle() { events += "closedIdle" }
        override fun onSettings(payload: JSONObject) { events += "settings" }
        override fun onUnauthorized() { events += "unauthorized" }
        override fun onError(message: String) { events += "error:$message" }
    }

    private val sockets = mutableListOf<FakeSocket>()
    private var switching = false
    private val live = LiveSocket(isSwitching = { switching }, create = { t, c -> FakeSocket(t, c).also { sockets += it } })
    private val target = SocketTarget("10.0.0.5", 3000, false, "tok")

    @Test
    fun `opens the socket against the target and connects it`() {
        live.open(target, Recorder())
        assertEquals(1, sockets.size)
        assertEquals(target, sockets[0].target)
        assertTrue(sockets[0].connected)
        assertTrue(live.isOpen)
    }

    @Test
    fun `a new socket closes the previous one first`() {
        live.open(target, Recorder())
        live.open(target.copy(port = 3001), Recorder())
        assertEquals(2, sockets.size)
        assertEquals(1, sockets[0].disconnects)
        assertEquals(0, sockets[1].disconnects)
    }

    @Test
    fun `close disconnects and forgets the socket and is safe twice`() {
        live.close()
        live.open(target, Recorder())
        live.close()
        live.close()
        assertEquals(1, sockets[0].disconnects)
        assertFalse(live.isOpen)
    }

    @Test
    fun `a close reports idle only when no server switch is under way`() {
        val rec = Recorder()
        live.open(target, rec)
        sockets[0].callbacks.onStatus(WsStatus.DISCONNECTED)
        assertEquals(listOf("status:DISCONNECTED", "closedIdle"), rec.events)
        rec.events.clear()
        switching = true
        sockets[0].callbacks.onStatus(WsStatus.DISCONNECTED)
        assertEquals(listOf("status:DISCONNECTED"), rec.events)
    }

    @Test
    fun `errors and expired tokens are ignored during a switch but settings and status still arrive`() {
        val rec = Recorder()
        live.open(target, rec)
        switching = true
        sockets[0].callbacks.onError("x")
        sockets[0].callbacks.onUnauthorized()
        sockets[0].callbacks.onSettings(JSONObject())
        sockets[0].callbacks.onStatus(WsStatus.CONNECTED)
        assertEquals(listOf("settings", "status:CONNECTED"), rec.events)
        switching = false
        sockets[0].callbacks.onError("y")
        sockets[0].callbacks.onUnauthorized()
        assertEquals(listOf("settings", "status:CONNECTED", "error:y", "unauthorized"), rec.events)
    }
}
