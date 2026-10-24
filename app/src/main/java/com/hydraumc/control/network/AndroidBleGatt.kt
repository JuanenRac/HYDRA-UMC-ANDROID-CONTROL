// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - BluetoothGatt implementation of the GATT abstraction
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.network

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.content.Context
import android.os.Build

/** [BleGattConnector] on top of Android's BluetoothGatt. Not covered by unit tests: it needs a phone. */
@SuppressLint("MissingPermission")
class AndroidBleGattConnector(private val context: Context) : BleGattConnector {
    override fun open(address: String, events: BleGattEvents): BleGattLink {
        val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
            ?: throw BleException(BleError(BleErrorKind.BLUETOOTH_UNAVAILABLE))
        if (!adapter.isEnabled) throw BleException(BleError(BleErrorKind.BLUETOOTH_OFF))
        if (!BluetoothAdapter.checkBluetoothAddress(address)) throw BleException(BleError(BleErrorKind.INVALID_ADDRESS))
        val link = AndroidBleGattLink(events)
        try {
            val device = adapter.getRemoteDevice(address)
            // BLUETOOTH_CONNECT can be revoked from Settings after the screen checked it: that is a
            // SecurityException here, not just a lint warning.
            link.gatt = device.connectGatt(context, false, link.callback, BluetoothDevice.TRANSPORT_LE)
                ?: throw BleException(BleError(BleErrorKind.CONNECT_FAILED))
        } catch (e: SecurityException) {
            throw BleException(BleError(BleErrorKind.PERMISSION_DENIED), e)
        } catch (e: IllegalArgumentException) {
            throw BleException(BleError(BleErrorKind.INVALID_ADDRESS), e)
        }
        return link
    }
}

@SuppressLint("MissingPermission")
private class AndroidBleGattLink(private val events: BleGattEvents) : BleGattLink {
    @Volatile var gatt: BluetoothGatt? = null
    private var closed = false

    private fun stateChar(): BluetoothGattCharacteristic? =
        gatt?.getService(BleContract.SERVICE_UUID)?.getCharacteristic(BleContract.STATE_CHAR_UUID)

    private fun ackChar(): BluetoothGattCharacteristic? =
        gatt?.getService(BleContract.SERVICE_UUID)?.getCharacteristic(BleContract.ACK_CHAR_UUID)

    private fun commandChar(): BluetoothGattCharacteristic? =
        gatt?.getService(BleContract.SERVICE_UUID)?.getCharacteristic(BleContract.COMMAND_CHAR_UUID)

    val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> events.onConnectionState(true, status)
                BluetoothProfile.STATE_DISCONNECTED -> events.onConnectionState(false, status)
            }
        }

        override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) = events.onMtu(mtu, status)

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) = events.onServicesDiscovered(status)

        override fun onDescriptorWrite(g: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            if (descriptor.uuid != BleContract.CCCD_UUID) return
            if (descriptor.characteristic?.uuid == BleContract.ACK_CHAR_UUID) events.onAckNotificationsEnabled(status)
            else events.onNotificationsEnabled(status)
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            @Suppress("DEPRECATION")
            deliver(characteristic, characteristic.value ?: ByteArray(0))
        }

        // Not calling super on purpose: its default would call the deprecated overload above and
        // deliver every frame twice.
        override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            deliver(characteristic, value)
        }

        private fun deliver(characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            if (characteristic.uuid == BleContract.ACK_CHAR_UUID) events.onAckFrame(value) else events.onStateFrame(value)
        }

        override fun onCharacteristicWrite(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) =
            events.onWriteDone(status)
    }

    override fun requestMtu(mtu: Int): Boolean = try {
        gatt?.requestMtu(mtu) == true
    } catch (_: SecurityException) {
        false
    }

    override fun discoverServices(): Boolean = try {
        gatt?.discoverServices() == true
    } catch (_: SecurityException) {
        false
    }

    override fun findContract(): BleContractLookup {
        val service = gatt?.getService(BleContract.SERVICE_UUID) ?: return BleContractLookup.NO_SERVICE
        val state = service.getCharacteristic(BleContract.STATE_CHAR_UUID)
        val command = service.getCharacteristic(BleContract.COMMAND_CHAR_UUID)
        val canNotify = state != null && state.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0
        val canWrite = command != null && command.properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0
        return if (canNotify && canWrite) BleContractLookup.FOUND else BleContractLookup.CHARACTERISTIC_MISSING
    }

    override fun hasAckCharacteristic(): Boolean {
        val ack = ackChar() ?: return false
        return ack.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0
    }

    override fun enableStateNotifications(): Boolean = enableNotifications(stateChar())

    override fun enableAckNotifications(): Boolean = enableNotifications(ackChar())

    private fun enableNotifications(char: BluetoothGattCharacteristic?): Boolean = try {
        val g = gatt
        val cccd = char?.getDescriptor(BleContract.CCCD_UUID)
        if (g == null || char == null || cccd == null || !g.setCharacteristicNotification(char, true)) {
            false
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            g.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) == BluetoothStatusCodes.SUCCESS
        } else {
            @Suppress("DEPRECATION")
            cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            @Suppress("DEPRECATION")
            g.writeDescriptor(cccd)
        }
    } catch (_: SecurityException) {
        false
    }

    override fun writeCommand(frame: ByteArray): Boolean = try {
        val g = gatt
        val char = commandChar()
        if (g == null || char == null) {
            false
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            g.writeCharacteristic(char, frame, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) == BluetoothStatusCodes.SUCCESS
        } else {
            @Suppress("DEPRECATION")
            char.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            @Suppress("DEPRECATION")
            char.value = frame
            @Suppress("DEPRECATION")
            g.writeCharacteristic(char)
        }
    } catch (_: SecurityException) {
        false
    }

    override fun close() {
        if (closed) return
        closed = true
        try {
            gatt?.disconnect()
        } catch (_: SecurityException) {
        }
        // A GATT client that is not closed keeps one of the few slots Android grants an app.
        try {
            gatt?.close()
        } catch (_: SecurityException) {
        }
        gatt = null
    }
}
