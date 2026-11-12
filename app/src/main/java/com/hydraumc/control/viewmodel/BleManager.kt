// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - viewmodel/BleManager.kt
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.viewmodel

import android.content.Context
import com.hydraumc.control.network.BleError
import com.hydraumc.control.network.HydraBleClient
import com.hydraumc.control.network.WsStatus

/** One Bluetooth link to a controller: it opens and closes, nothing more. */
interface BleSession {
    fun connect()
    fun disconnect()
}

/** Makes the session for a device. The app's one builds the real GATT client; the tests hand in a stand-in. */
fun interface BleSessionFactory {
    fun create(
        deviceAddress: String,
        onStatus: (WsStatus) -> Unit,
        onSettings: (String) -> Unit,
        onError: (BleError) -> Unit,
    ): BleSession
}

/**
 * Holds the one Bluetooth session of the app, away from the view model: opening a session always closes the previous one,
 * closing it twice is harmless, and nothing is kept once it is closed. It has never run on a phone against a real device
 * (see docs/BLE_CONTRACT.md).
 */
class BleManager(private val factory: BleSessionFactory) {
    private var session: BleSession? = null

    /** True while a session is held (opened and not closed). */
    val isOpen: Boolean get() = session != null

    /** Closes the session held, if any, then opens one to [deviceAddress]. */
    fun open(
        deviceAddress: String,
        onStatus: (WsStatus) -> Unit,
        onSettings: (String) -> Unit,
        onError: (BleError) -> Unit,
    ) {
        close()
        session = factory.create(deviceAddress, onStatus, onSettings, onError).also { it.connect() }
    }

    /** Closes the session held, if any. */
    fun close() {
        session?.disconnect()
        session = null
    }

    companion object {
        /** The real GATT client of the app, seen as a [BleSession]. */
        fun forAndroid(context: Context): BleManager = BleManager { address, onStatus, onSettings, onError ->
            val client = HydraBleClient.forAndroid(context, address, onStatus, onSettings, onError)
            object : BleSession {
                override fun connect() = client.connect()
                override fun disconnect() = client.disconnect()
            }
        }
    }
}
