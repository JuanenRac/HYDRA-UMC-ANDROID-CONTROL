// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - Watch companion version-status wire contract
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0 - see LICENSE
// =============================================================================
package com.hydraumc.control.wear

import org.json.JSONObject

/**
 * Shared message contract for a future authenticated phone-to-watch channel.
 *
 * It intentionally reports status only. A phone must never transmit or
 * install a Wear OS APK through this channel; the documented ADB updater is
 * the supported no-Play/no-MDM deployment path.
 */
data class WatchCompanionVersionStatus(
    val protocolVersion: Int = CURRENT_PROTOCOL_VERSION,
    val appVersion: String,
    val updateAvailable: Boolean,
) {
    init {
        require(protocolVersion == CURRENT_PROTOCOL_VERSION) {
            "Unsupported companion protocol version: $protocolVersion"
        }
        require(STABLE_VERSION.matches(appVersion)) {
            "appVersion must be a stable MAJOR.MINOR.PATCH value"
        }
    }

    fun toWireJson(): String = JSONObject()
        .put("type", MESSAGE_TYPE)
        .put("protocolVersion", protocolVersion)
        .put("appVersion", appVersion)
        .put("updateAvailable", updateAvailable)
        .toString()

    companion object {
        const val CURRENT_PROTOCOL_VERSION = 1
        const val MESSAGE_TYPE = "companion_version_status"
        private val STABLE_VERSION = Regex("^\\d+\\.\\d+\\.\\d+$")
    }
}
