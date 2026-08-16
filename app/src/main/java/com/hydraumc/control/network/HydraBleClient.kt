// =============================================================================
// HYDRA-UMC CONTROL - Bluetooth Low Energy client for GATT communication
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0 - see LICENSE
// =============================================================================
package com.hydraumc.control.network

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import android.os.Build
import android.util.Log
import java.util.*

/**
 * Handles Bluetooth LE communication with a HYDRA-UMC device.
 * Mirrored after HydraApiClient/HydraWebSocket but using GATT.
 * 
 * @property context The application context.
 * @property deviceAddress The MAC address of the target BLE device.
 * @property onStatus Callback to report connection status changes.
 * @property onSettings Callback for received settings updates.
 * @property onError Callback for error reporting.
 */
@SuppressLint("MissingPermission")
class HydraBleClient(
    private val context: Context,
    private val deviceAddress: String,
    private val onStatus: (WsStatus) -> Unit,
    private val onSettings: (String) -> Unit,
    private val onError: (String) -> Unit = {}
) {
    /** The active GATT connection. */
    private var bluetoothGatt: BluetoothGatt? = null
    
    companion object {
        /** Placeholder Service UUID for future HYDRA-UMC implementations. */
        val HYDRA_SERVICE_UUID: UUID = UUID.fromString("0000180a-0000-1000-8000-00805f9b34fb") // Placeholder (Device Info Service)
        /** Placeholder Characteristic UUID for future HYDRA-UMC implementations. */
        val HYDRA_STATE_CHAR_UUID: UUID = UUID.fromString("00002a29-0000-1000-8000-00805f9b34fb") // Placeholder (Manufacturer Name String)
    }

    /** Callback object for GATT events. */
    private val gattCallback = object : BluetoothGattCallback() {
        /** 
         * Reports changes in the GATT connection state. 
         */
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                onStatus(WsStatus.CONNECTED)
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                onStatus(WsStatus.DISCONNECTED)
                bluetoothGatt = null
            }
        }

        /** 
         * Reports completion of service discovery. 
         */
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d("HydraBleClient", "Services discovered")
                // Here we would enable notifications for the state characteristic
            } else {
                onError("GATT Service Discovery failed: $status")
            }
        }

        /** 
         * Handles characteristic updates for legacy Android versions. 
         */
        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            // Keep for compatibility with older APIs if needed
            val payload = characteristic.getStringValue(0) ?: ""
            onSettings(payload)
        }

        /** 
         * Handles characteristic updates for modern Android versions (13+). 
         */
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            val payload = String(value, Charsets.UTF_8)
            onSettings(payload)
        }
    }

    /** 
     * Initiates a connection to the BLE device. 
     */
    fun connect() {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = bluetoothManager?.adapter
        if (adapter == null) {
            onError("Bluetooth Adapter not found")
            return
        }
        val device = adapter.getRemoteDevice(deviceAddress)
        onStatus(WsStatus.CONNECTING)
        bluetoothGatt = device.connectGatt(context, false, gattCallback)
    }

    /** 
     * Closes the BLE connection and releases resources. 
     */
    fun disconnect() {
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
        onStatus(WsStatus.DISCONNECTED)
    }
    
    /** 
     * Sends a payload to the device via a GATT characteristic write.
     * @param payload The string data to send.
     * @return True if the write operation was initiated successfully.
     */
    fun send(payload: String): Boolean {
        val gatt = bluetoothGatt ?: return false
        val service = gatt.getService(HYDRA_SERVICE_UUID) ?: return false
        val char = service.getCharacteristic(HYDRA_STATE_CHAR_UUID) ?: return false
        
        /** Byte array representation of the payload. */
        val value = payload.toByteArray(Charsets.UTF_8)
        
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeCharacteristic(char, value, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) == BluetoothStatusCodes.SUCCESS
        } else {
            @Suppress("DEPRECATION")
            char.value = value
            @Suppress("DEPRECATION")
            gatt.writeCharacteristic(char)
        }
    }
}
