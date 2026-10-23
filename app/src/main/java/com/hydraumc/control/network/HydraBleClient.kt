// =============================================================================
// HYDRA-UMC CONTROL - Bluetooth Low Energy client for GATT communication
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.network

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Talks to one HYDRA-UMC device over the BLE contract ([BleContract], docs/BLE_CONTRACT.md):
 * connect, ask for a bigger MTU, discover and check the service, turn on state notifications,
 * reassemble the notified state and write framed commands one at a time. A link that drops by
 * itself is retried with a growing delay ([BleReconnectPolicy]); [disconnect] stops that.
 *
 * All platform access goes through [connector], so the whole state machine runs in unit tests
 * against a fake GATT. Every callback to the caller runs on [scope] (Main on a phone): GATT
 * callbacks arrive on Android's binder threads, and [onSettings]/[onStatus]/[onError] feed
 * RobotViewModel state that is not thread safe.
 */
class HydraBleClient(
    private val connector: BleGattConnector,
    private val deviceAddress: String,
    private val scope: CoroutineScope,
    private val onStatus: (WsStatus) -> Unit,
    private val onSettings: (String) -> Unit,
    private val onError: (BleError) -> Unit = {},
    private val reconnectPolicy: BleReconnectPolicy = BleReconnectPolicy.DEFAULT,
    private val preferredMtu: Int = BleContract.PREFERRED_MTU,
) {
    private var link: BleGattLink? = null
    /** Changes with every link: events of an older link are dropped. */
    private var generation = 0
    private var userClosed = true
    private var ready = false
    private var mtu = BleContract.DEFAULT_MTU
    private var attempts = 0
    private var reconnectJob: Job? = null
    private val reassembler = BleReassembler()
    private val writeQueue = ArrayDeque<ByteArray>()
    private var writeInFlight = false

    /** True once the service is checked and state notifications are on. */
    val isReady: Boolean get() = ready

    /** Starts the connection; a second call while one is running does nothing. */
    fun connect() {
        if (!userClosed) return
        userClosed = false
        attempts = 0
        openLink()
    }

    /** Closes the link and stops any retry. */
    fun disconnect() {
        userClosed = true
        reconnectJob?.cancel()
        reconnectJob = null
        generation++
        releaseLink()
        onStatus(WsStatus.DISCONNECTED)
    }

    /**
     * Sends [payload] (a JSON command) as framed writes. Returns false when the link is not ready or
     * the message is empty or too large; true only means "queued": a refused write is reported
     * through [onError] later.
     */
    fun send(payload: String): Boolean {
        if (!ready) {
            onError(BleError(BleErrorKind.NOT_CONNECTED))
            return false
        }
        val bytes = payload.toByteArray(Charsets.UTF_8)
        val frames = BleFraming.encode(bytes, mtu)
        if (frames.isEmpty()) {
            if (bytes.isNotEmpty()) onError(BleError(BleErrorKind.MESSAGE_TOO_LARGE))
            return false
        }
        writeQueue.addAll(frames)
        pumpWrites()
        return true
    }

    private fun openLink() {
        val gen = ++generation
        onStatus(WsStatus.CONNECTING)
        try {
            link = connector.open(deviceAddress, LinkEvents(gen))
        } catch (e: BleException) {
            link = null
            failPermanently(e.error)
        }
    }

    private fun releaseLink() {
        val old = link
        link = null
        ready = false
        mtu = BleContract.DEFAULT_MTU
        writeQueue.clear()
        writeInFlight = false
        reassembler.reset()
        old?.close()
    }

    /** An error no retry can fix: report it and stay disconnected. */
    private fun failPermanently(error: BleError) {
        userClosed = true
        generation++
        releaseLink()
        onStatus(WsStatus.DISCONNECTED)
        onError(error)
    }

    /** The link is gone (device out of range, powered off, failed to connect): retry or give up. */
    private fun linkLost(error: BleError) {
        releaseLink()
        if (userClosed) return
        if (reconnectPolicy.shouldRetry(attempts)) {
            attempts++
            onStatus(WsStatus.CONNECTING)
            reconnectJob = scope.launch {
                delay(reconnectPolicy.delayMs(attempts))
                if (!userClosed) openLink()
            }
        } else {
            userClosed = true
            onStatus(WsStatus.DISCONNECTED)
            onError(BleError(BleErrorKind.RECONNECT_GAVE_UP, error.status))
        }
    }

    private fun pumpWrites() {
        if (writeInFlight) return
        val frame = writeQueue.removeFirstOrNull() ?: return
        val started = link?.writeCommand(frame) == true
        if (started) {
            writeInFlight = true
        } else {
            writeQueue.clear()
            onError(BleError(BleErrorKind.WRITE_FAILED))
        }
    }

    private inner class LinkEvents(private val gen: Int) : BleGattEvents {
        private fun post(block: () -> Unit) {
            scope.launch { if (gen == generation) block() }
        }

        override fun onConnectionState(connected: Boolean, status: Int) = post {
            if (!connected) {
                linkLost(BleError(BleErrorKind.CONNECT_FAILED, status.takeIf { it != 0 }))
            } else if (link?.requestMtu(preferredMtu) != true) {
                startDiscovery()
            }
        }

        override fun onMtu(mtu: Int, status: Int) = post {
            if (status == 0 && mtu > BleContract.ATT_OVERHEAD + 1) this@HydraBleClient.mtu = mtu
            startDiscovery()
        }

        override fun onServicesDiscovered(status: Int) = post {
            if (status != 0) {
                linkLost(BleError(BleErrorKind.SERVICE_DISCOVERY_FAILED, status))
                return@post
            }
            when (link?.findContract()) {
                BleContractLookup.FOUND ->
                    if (link?.enableStateNotifications() != true) failPermanently(BleError(BleErrorKind.NOTIFICATIONS_FAILED))
                BleContractLookup.NO_SERVICE -> failPermanently(BleError(BleErrorKind.NOT_A_HYDRA_DEVICE))
                else -> failPermanently(BleError(BleErrorKind.CHARACTERISTIC_MISSING))
            }
        }

        override fun onNotificationsEnabled(status: Int) = post {
            if (status != 0) {
                failPermanently(BleError(BleErrorKind.NOTIFICATIONS_FAILED, status))
            } else {
                ready = true
                attempts = 0
                onStatus(WsStatus.CONNECTED)
            }
        }

        override fun onStateFrame(frame: ByteArray) = post {
            when (val result = reassembler.push(frame)) {
                is BleReassembler.Result.Message -> onSettings(String(result.bytes, Charsets.UTF_8))
                BleReassembler.Result.Malformed -> onError(BleError(BleErrorKind.MALFORMED_FRAME))
                BleReassembler.Result.Pending -> Unit
            }
        }

        override fun onWriteDone(status: Int) = post {
            writeInFlight = false
            if (status != 0) {
                // The rest of a message whose frame failed is useless to the device.
                writeQueue.clear()
                onError(BleError(BleErrorKind.WRITE_FAILED, status))
            } else {
                pumpWrites()
            }
        }

        private fun startDiscovery() {
            if (link?.discoverServices() != true) linkLost(BleError(BleErrorKind.SERVICE_DISCOVERY_FAILED))
        }
    }

    companion object {
        /** The client the app uses: real GATT, callbacks on Main. */
        fun forAndroid(
            context: Context,
            deviceAddress: String,
            onStatus: (WsStatus) -> Unit,
            onSettings: (String) -> Unit,
            onError: (BleError) -> Unit,
        ): HydraBleClient = HydraBleClient(
            connector = AndroidBleGattConnector(context.applicationContext),
            deviceAddress = deviceAddress,
            scope = CoroutineScope(Dispatchers.Main.immediate),
            onStatus = onStatus,
            onSettings = onSettings,
            onError = onError,
        )
    }
}
