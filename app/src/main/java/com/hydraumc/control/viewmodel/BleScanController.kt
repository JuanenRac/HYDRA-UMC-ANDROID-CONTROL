// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - viewmodel/BleScanController.kt
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.viewmodel

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.ParcelUuid
import androidx.compose.runtime.MutableState
import androidx.core.content.ContextCompat
import com.hydraumc.control.model.BleDevice
import com.hydraumc.control.network.BleContract
import com.hydraumc.control.network.BleDeviceList
import com.hydraumc.control.network.BleError
import com.hydraumc.control.network.BleErrorKind
import com.hydraumc.control.network.BlePermissions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The Bluetooth LE scan, away from the view model: the checks that come before it (adapter, permissions, location services
 * on the Android versions that need them), the scan itself with the HYDRA service filter, and the stop after a few seconds.
 * The lists and flags the Bluetooth tab shows are handed in, so the screen keeps reading the same state.
 * It has never run on a phone against a real device (see docs/BLE_CONTRACT.md).
 */
class BleScanController(
    private val context: Context,
    private val scope: CoroutineScope,
    private val devices: MutableState<List<BleDevice>>,
    private val scanning: MutableState<Boolean>,
    private val bluetoothEnabled: MutableState<Boolean>,
    private val showAllDevices: () -> Boolean,
    private val onError: (BleError) -> Unit,
    private val onScanStarted: () -> Unit,
    private val scanSeconds: Long = 5L,
) {
    private var callback: ScanCallback? = null

    /** The permissions of [needed] that the person has not granted. */
    fun missingPermissions(needed: List<String>): List<String> =
        BlePermissions.missing(needed) { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }

    @SuppressLint("MissingPermission")
    fun start() {
        if (scanning.value) return

        val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
        if (adapter == null) {
            bluetoothEnabled.value = false
            onError(BleError(BleErrorKind.BLUETOOTH_UNAVAILABLE))
            return
        }
        if (!adapter.isEnabled) {
            bluetoothEnabled.value = false
            return
        }
        bluetoothEnabled.value = true

        if (missingPermissions(BlePermissions.scanPermissions(Build.VERSION.SDK_INT)).isNotEmpty()) {
            onError(BleError(BleErrorKind.PERMISSION_DENIED))
            return
        }

        // On API 24-30, BLE scan results are silently withheld system-wide if Location Services (GPS) is off at the OS
        // level, even with the runtime permission already granted - startScan() itself reports no error, it just never
        // calls onScanResult(). API 31+ dropped this requirement once BLUETOOTH_SCAN(neverForLocation) replaced the old
        // location-permission-based BLE scan model.
        if (BlePermissions.needsLocationServices(Build.VERSION.SDK_INT)) {
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            if (locationManager == null || !androidx.core.location.LocationManagerCompat.isLocationEnabled(locationManager)) {
                onError(BleError(BleErrorKind.LOCATION_OFF))
                return
            }
        }

        val scanner = adapter.bluetoothLeScanner
        if (scanner == null) {
            onError(BleError(BleErrorKind.SCANNER_UNAVAILABLE))
            return
        }

        onScanStarted()
        scanning.value = true
        devices.value = emptyList()

        val scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                // The advertised name needs no permission; the cached device name needs BLUETOOTH_CONNECT on Android 12+
                // and may have been revoked meanwhile.
                val name = result.scanRecord?.deviceName ?: try { result.device.name } catch (_: SecurityException) { null }
                devices.value = BleDeviceList.upsert(devices.value, BleDevice(name, result.device.address, result.rssi))
            }

            override fun onScanFailed(errorCode: Int) {
                callback = null
                scanning.value = false
                onError(BleError(BleErrorKind.SCAN_FAILED, errorCode))
            }
        }
        callback = scanCallback

        // Only devices advertising the HYDRA service, unless the user asked for everything.
        val filters = if (showAllDevices()) emptyList() else listOf(
            ScanFilter.Builder().setServiceUuid(ParcelUuid(BleContract.SERVICE_UUID)).build(),
        )
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()

        scope.launch {
            try {
                scanner.startScan(filters, settings, scanCallback)
            } catch (e: SecurityException) {
                // BLUETOOTH_SCAN was revoked from Settings after the screen checked it.
                callback = null
                scanning.value = false
                onError(BleError(BleErrorKind.PERMISSION_DENIED))
                return@launch
            }
            delay(scanSeconds * 1_000L)
            stop()
        }
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        if (!scanning.value) return
        val scanner = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter?.bluetoothLeScanner
        try {
            callback?.let { scanner?.stopScan(it) }
        } catch (_: SecurityException) {
            // Permission revoked while scanning: the scan stops with the app anyway.
        }
        callback = null
        scanning.value = false
    }
}
