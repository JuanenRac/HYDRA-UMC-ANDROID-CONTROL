// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - Runtime permissions of Bluetooth LE, by Android version
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.network

import com.hydraumc.control.model.BleDevice

/** The permissions BLE needs on a given Android version, kept free of Android classes so tests can run them. */
object BlePermissions {
    const val SCAN = "android.permission.BLUETOOTH_SCAN"
    const val CONNECT = "android.permission.BLUETOOTH_CONNECT"
    const val FINE_LOCATION = "android.permission.ACCESS_FINE_LOCATION"
    private const val ANDROID_12 = 31

    /** Android 12+ asks for scan and connect; older versions tie scanning to the location permission. */
    fun scanPermissions(sdk: Int): List<String> = if (sdk >= ANDROID_12) listOf(SCAN) else listOf(FINE_LOCATION)

    /** Connecting needs a permission only from Android 12. */
    fun connectPermissions(sdk: Int): List<String> = if (sdk >= ANDROID_12) listOf(CONNECT) else emptyList()

    /** Everything the Bluetooth tab asks for at once. */
    fun all(sdk: Int): List<String> = (scanPermissions(sdk) + connectPermissions(sdk)).distinct()

    /** What is still missing among [needed], given a way to ask whether one is granted. */
    fun missing(needed: List<String>, isGranted: (String) -> Boolean): List<String> = needed.filterNot(isGranted)

    /** Android 11 and older drop scan results while Location Services are off, with no error. */
    fun needsLocationServices(sdk: Int): Boolean = sdk < ANDROID_12
}

/** The list of devices a scan found. */
object BleDeviceList {
    /** Adds [found] or refreshes the entry with the same address (signal, and the name once it is known); strongest first. */
    fun upsert(current: List<BleDevice>, found: BleDevice): List<BleDevice> {
        val previous = current.firstOrNull { it.address == found.address }
        val merged = found.copy(name = found.name ?: previous?.name)
        return (current.filterNot { it.address == found.address } + merged).sortedByDescending { it.rssi }
    }
}
