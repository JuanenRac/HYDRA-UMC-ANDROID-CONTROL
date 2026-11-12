// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - app/src/test/java/com/hydraumc/control/viewmodel/BleManagerTest.kt
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.viewmodel

import com.hydraumc.control.network.BleError
import com.hydraumc.control.network.BleErrorKind
import com.hydraumc.control.network.WsStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Opening a Bluetooth session closes the one before it, closing is harmless twice, and what the session reports reaches the caller. */
class BleManagerTest {
    private class FakeSession(val address: String, val log: MutableList<String>) : BleSession {
        override fun connect() { log += "connect $address" }
        override fun disconnect() { log += "disconnect $address" }
    }

    private val log = mutableListOf<String>()
    private var lastStatus: ((WsStatus) -> Unit)? = null
    private var lastSettings: ((String) -> Unit)? = null
    private var lastError: ((BleError) -> Unit)? = null
    private val manager = BleManager { address, onStatus, onSettings, onError ->
        lastStatus = onStatus
        lastSettings = onSettings
        lastError = onError
        FakeSession(address, log)
    }

    private fun open(
        address: String,
        statuses: MutableList<WsStatus> = mutableListOf(),
        settings: MutableList<String> = mutableListOf(),
        errors: MutableList<BleError> = mutableListOf(),
    ) = manager.open(address, { statuses += it }, { settings += it }, { errors += it })

    @Test
    fun openingConnectsTheSessionAndHoldsIt() {
        assertFalse(manager.isOpen)
        open("AA")
        assertTrue(manager.isOpen)
        assertEquals(listOf("connect AA"), log)
    }

    @Test
    fun openingAnotherClosesThePreviousOneFirst() {
        open("AA")
        open("BB")
        assertEquals(listOf("connect AA", "disconnect AA", "connect BB"), log)
    }

    @Test
    fun closingTwiceDisconnectsOnce() {
        open("AA")
        manager.close()
        manager.close()
        assertFalse(manager.isOpen)
        assertEquals(listOf("connect AA", "disconnect AA"), log)
    }

    @Test
    fun closingWithNothingOpenDoesNothing() {
        manager.close()
        assertTrue(log.isEmpty())
    }

    @Test
    fun whatTheSessionReportsReachesTheCaller() {
        val statuses = mutableListOf<WsStatus>()
        val settings = mutableListOf<String>()
        val errors = mutableListOf<BleError>()
        open("AA", statuses, settings, errors)
        lastStatus!!(WsStatus.CONNECTED)
        lastSettings!!("{}")
        lastError!!(BleError(BleErrorKind.PERMISSION_DENIED))
        assertEquals(listOf(WsStatus.CONNECTED), statuses)
        assertEquals(listOf("{}"), settings)
        assertEquals(BleErrorKind.PERMISSION_DENIED, errors.single().kind)
    }

    @Test
    fun aClosedManagerCanOpenAgain() {
        open("AA")
        manager.close()
        open("AA")
        assertEquals(listOf("connect AA", "disconnect AA", "connect AA"), log)
        assertTrue(manager.isOpen)
    }
}
