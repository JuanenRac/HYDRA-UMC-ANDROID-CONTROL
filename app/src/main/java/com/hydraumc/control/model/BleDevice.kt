// =============================================================================
// HYDRA-UMC CONTROL - Data model for discovered Bluetooth Low Energy devices
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0 - see LICENSE
// =============================================================================
package com.hydraumc.control.model

/** 
 * Simple representation of a discovered BLE device. 
 * @property name The optional name of the device.
 * @property address The hardware MAC address of the device.
 * @property rssi The signal strength indicator.
 */
data class BleDevice(
    val name: String?,
    val address: String,
    val rssi: Int
) {
    /** 
     * Computed property that returns the device name or a fallback placeholder. 
     */
    val displayName: String get() = name ?: "Unknown Device"
}
