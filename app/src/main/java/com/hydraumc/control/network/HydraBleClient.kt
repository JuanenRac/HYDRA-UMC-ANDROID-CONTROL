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
    private val ackTimeoutMs: Long = BleCommands.DEFAULT_ACK_TIMEOUT_MS,
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
    private val ackReassembler = BleReassembler()
    /** True once the ACK characteristic is on: only then can a command be sent and acknowledged. */
    private var ackReady = false
    private var commandCounter = 0
    private class PendingCommand(val onResult: (BleCommandOutcome) -> Unit, var timeout: Job?)
    private val pendingCommands = HashMap<String, PendingCommand>()
    private val writeQueue = ArrayDeque<ByteArray>()
    private var writeInFlight = false

    /** True once the service is checked and state notifications are on. */
    val isReady: Boolean get() = ready

    /** True when commands can be sent and acknowledged over this link. */
    val canSendCommands: Boolean get() = ready && ackReady

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

    /**
     * Sends one robot command and reports what became of it: [BleCommandOutcome.Acknowledged] only after the peripheral
     * answered (and `ack.ok` says whether the server applied it), [BleCommandOutcome.NoAnswer] when nothing answered
     * within the timeout or the link dropped (the command counts as NOT delivered: a STOP nobody confirmed must not look
     * confirmed), [BleCommandOutcome.NotAvailable] or [BleCommandOutcome.TooLarge] when nothing was sent. The BLE write
     * response only proves that the radio delivered a frame, so it is never taken as an acknowledgement.
     */
    fun sendCommand(request: BleCommandRequest, onResult: (BleCommandOutcome) -> Unit) {
        if (!canSendCommands) {
            onResult(BleCommandOutcome.NotAvailable)
            return
        }
        val id = "a-${++commandCounter}-${System.nanoTime().toString(36)}".take(BleCommands.MAX_ID_CHARS)
        val bytes = BleCommands.encode(id, request)
        val frames = BleFraming.encode(bytes, mtu)
        if (frames.isEmpty()) {
            onResult(BleCommandOutcome.TooLarge)
            return
        }
        val pending = PendingCommand(onResult, null)
        pendingCommands[id] = pending
        pending.timeout = scope.launch {
            delay(ackTimeoutMs)
            pendingCommands.remove(id)?.onResult?.invoke(BleCommandOutcome.NoAnswer)
        }
        writeQueue.addAll(frames)
        pumpWrites()
    }

    private fun failPendingCommands() {
        if (pendingCommands.isEmpty()) return
        val all = pendingCommands.values.toList()
        pendingCommands.clear()
        for (pending in all) {
            pending.timeout?.cancel()
            pending.onResult(BleCommandOutcome.NoAnswer)
        }
    }

    private fun markConnected() {
        ready = true
        attempts = 0
        onStatus(WsStatus.CONNECTED)
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
        ackReady = false
        mtu = BleContract.DEFAULT_MTU
        writeQueue.clear()
        writeInFlight = false
        reassembler.reset()
        ackReassembler.reset()
        failPendingCommands()
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
            } else if (link?.hasAckCharacteristic() == true && link?.enableAckNotifications() == true) {
                // The state is on. The device also takes commands: its acknowledgements are turned on before the link is
                // reported connected (one descriptor write at a time).
            } else {
                markConnected()
            }
        }

        override fun onAckNotificationsEnabled(status: Int) = post {
            // A device whose acknowledgements cannot be turned on still shows its state; it just cannot take commands.
            ackReady = status == 0
            markConnected()
        }

        override fun onAckFrame(frame: ByteArray) = post {
            when (val result = ackReassembler.push(frame)) {
                is BleReassembler.Result.Message -> {
                    val ack = BleCommands.parseAck(result.bytes) ?: return@post
                    val id = ack.id ?: return@post
                    val pending = pendingCommands.remove(id) ?: return@post
                    pending.timeout?.cancel()
                    pending.onResult(BleCommandOutcome.Acknowledged(ack))
                }
                BleReassembler.Result.Malformed -> onError(BleError(BleErrorKind.MALFORMED_FRAME))
                BleReassembler.Result.Pending -> Unit
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
