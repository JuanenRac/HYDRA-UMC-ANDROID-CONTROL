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
 */
@SuppressLint("MissingPermission")
class HydraBleClient(
    private val context: Context,
    private val deviceAddress: String,
    private val onStatus: (WsStatus) -> Unit,
    private val onSettings: (String) -> Unit,
    private val onError: (String) -> Unit = {}
) {
    private var bluetoothGatt: BluetoothGatt? = null
    
    // Planned UUIDs for HYDRA-UMC BLE Service
    companion object {
        val HYDRA_SERVICE_UUID: UUID = UUID.fromString("0000180a-0000-1000-8000-00805f9b34fb") // Placeholder (Device Info Service)
        val HYDRA_STATE_CHAR_UUID: UUID = UUID.fromString("00002a29-0000-1000-8000-00805f9b34fb") // Placeholder (Manufacturer Name String)
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                onStatus(WsStatus.CONNECTED)
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                onStatus(WsStatus.DISCONNECTED)
                bluetoothGatt = null
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d("HydraBleClient", "Services discovered")
                // Here we would enable notifications for the state characteristic
            } else {
                onError("GATT Service Discovery failed: $status")
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            // Keep for compatibility with older APIs if needed
            val payload = characteristic.getStringValue(0) ?: ""
            onSettings(payload)
        }

        // Modern API for characteristic changes (Android 13+)
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            val payload = String(value, Charsets.UTF_8)
            onSettings(payload)
        }
    }

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

    fun disconnect() {
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
        onStatus(WsStatus.DISCONNECTED)
    }
    
    fun send(payload: String): Boolean {
        val gatt = bluetoothGatt ?: return false
        val service = gatt.getService(HYDRA_SERVICE_UUID) ?: return false
        val char = service.getCharacteristic(HYDRA_STATE_CHAR_UUID) ?: return false
        
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
