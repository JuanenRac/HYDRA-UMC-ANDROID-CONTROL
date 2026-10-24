// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - GATT abstraction so the BLE logic runs without a phone
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.network

/** What the contract lookup found on a discovered device. */
enum class BleContractLookup { FOUND, NO_SERVICE, CHARACTERISTIC_MISSING }

/** Events of one GATT link, in the order the platform delivers them (any thread). */
interface BleGattEvents {
    fun onConnectionState(connected: Boolean, status: Int)
    fun onMtu(mtu: Int, status: Int)
    fun onServicesDiscovered(status: Int)
    fun onNotificationsEnabled(status: Int)
    /** Outcome of turning on the notifications of the ACK characteristic. */
    fun onAckNotificationsEnabled(status: Int)
    /** One frame notified on the STATE characteristic. */
    fun onStateFrame(frame: ByteArray)
    /** One frame notified on the ACK characteristic. */
    fun onAckFrame(frame: ByteArray)
    /** Outcome of the last COMMAND write. */
    fun onWriteDone(status: Int)
}

/** One GATT client. Each method returns false when the platform refused to start the operation. */
interface BleGattLink {
    fun requestMtu(mtu: Int): Boolean
    fun discoverServices(): Boolean
    fun findContract(): BleContractLookup
    fun enableStateNotifications(): Boolean
    /** Whether the device has the optional ACK characteristic (it can take commands). */
    fun hasAckCharacteristic(): Boolean
    fun enableAckNotifications(): Boolean
    fun writeCommand(frame: ByteArray): Boolean
    /** Disconnects and releases the GATT client. Safe to call twice. */
    fun close()
}

/** Opens links. The Android one wraps BluetoothGatt; tests use a fake. */
fun interface BleGattConnector {
    /** @throws BleException when the link cannot be started (no adapter, Bluetooth off, no permission, bad address). */
    fun open(address: String, events: BleGattEvents): BleGattLink
}
