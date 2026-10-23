// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - Text of the Bluetooth errors, in the language of the phone
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.util

import android.content.Context
import androidx.annotation.StringRes
import com.hydraumc.control.R
import com.hydraumc.control.network.BleError
import com.hydraumc.control.network.BleErrorKind

/** The string resource that explains [kind]. */
@StringRes
fun bleErrorResource(kind: BleErrorKind): Int = when (kind) {
    BleErrorKind.BLUETOOTH_UNAVAILABLE -> R.string.ble_error_unavailable
    BleErrorKind.BLUETOOTH_OFF -> R.string.ble_error_off
    BleErrorKind.PERMISSION_DENIED -> R.string.ble_error_permission
    BleErrorKind.LOCATION_OFF -> R.string.ble_error_location_off
    BleErrorKind.INVALID_ADDRESS -> R.string.ble_error_invalid_address
    BleErrorKind.SCANNER_UNAVAILABLE -> R.string.ble_error_scanner
    BleErrorKind.SCAN_FAILED -> R.string.ble_error_scan_failed
    BleErrorKind.CONNECT_FAILED -> R.string.ble_error_connect_failed
    BleErrorKind.SERVICE_DISCOVERY_FAILED -> R.string.ble_error_discovery_failed
    BleErrorKind.NOT_A_HYDRA_DEVICE -> R.string.ble_error_not_hydra
    BleErrorKind.CHARACTERISTIC_MISSING -> R.string.ble_error_characteristic
    BleErrorKind.NOTIFICATIONS_FAILED -> R.string.ble_error_notifications
    BleErrorKind.WRITE_FAILED -> R.string.ble_error_write_failed
    BleErrorKind.NOT_CONNECTED -> R.string.ble_error_not_connected
    BleErrorKind.MESSAGE_TOO_LARGE -> R.string.ble_error_too_large
    BleErrorKind.MALFORMED_FRAME -> R.string.ble_error_malformed
    BleErrorKind.RECONNECT_GAVE_UP -> R.string.ble_error_gave_up
}

/** The message for [error], with the platform status code appended when there is one. */
fun bleErrorMessage(context: Context, error: BleError): String {
    val text = context.getString(bleErrorResource(error.kind))
    val status = error.status ?: return text
    return context.getString(R.string.ble_error_with_status, text, status)
}
