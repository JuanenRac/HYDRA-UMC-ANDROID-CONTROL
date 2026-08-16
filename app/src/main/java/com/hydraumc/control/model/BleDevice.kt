package com.hydraumc.control.model

/** Simple representation of a discovered BLE device. */
data class BleDevice(
    val name: String?,
    val address: String,
    val rssi: Int
) {
    val displayName: String get() = name ?: "Unknown Device"
}
