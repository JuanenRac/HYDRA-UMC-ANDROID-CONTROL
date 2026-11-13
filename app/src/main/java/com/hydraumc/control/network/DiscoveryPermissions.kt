// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - network/DiscoveryPermissions.kt
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.network

/**
 * The runtime permissions the discovery of servers, Bluetooth LE scanning and notifications need, for an API level.
 * Kept apart from the activity so the list can be checked without a device.
 *
 * From Android 17 (API 37), an app that targets it needs ACCESS_LOCAL_NETWORK to talk to devices on the local network;
 * the server of the robot cell is one, so it is requested from there on (older systems do not know the permission).
 */
object DiscoveryPermissions {
    const val ACCESS_LOCAL_NETWORK = "android.permission.ACCESS_LOCAL_NETWORK"
    const val NEARBY_WIFI_DEVICES = "android.permission.NEARBY_WIFI_DEVICES"
    const val POST_NOTIFICATIONS = "android.permission.POST_NOTIFICATIONS"
    const val FINE_LOCATION = "android.permission.ACCESS_FINE_LOCATION"
    const val COARSE_LOCATION = "android.permission.ACCESS_COARSE_LOCATION"
    const val BLUETOOTH_SCAN = "android.permission.BLUETOOTH_SCAN"
    const val BLUETOOTH_CONNECT = "android.permission.BLUETOOTH_CONNECT"

    private const val API_S = 31
    private const val API_TIRAMISU = 33
    private const val API_LOCAL_NETWORK = 37

    fun forSdk(sdk: Int): List<String> {
        val permissions = mutableListOf(FINE_LOCATION, COARSE_LOCATION)
        if (sdk >= API_S) {
            permissions.add(BLUETOOTH_SCAN)
            permissions.add(BLUETOOTH_CONNECT)
        }
        if (sdk >= API_TIRAMISU) {
            permissions.add(NEARBY_WIFI_DEVICES)
            permissions.add(POST_NOTIFICATIONS)
        }
        if (sdk >= API_LOCAL_NETWORK) {
            permissions.add(ACCESS_LOCAL_NETWORK)
        }
        return permissions
    }
}
