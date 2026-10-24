// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - BLE contract: UUIDs, message framing and errors
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.network

import java.io.ByteArrayOutputStream
import java.util.UUID

/**
 * The Bluetooth LE contract between this app and a HYDRA-UMC device (see docs/BLE_CONTRACT.md).
 *
 * One GATT service with two characteristics:
 *  - STATE (read, notify): the robot state as UTF-8 JSON, the same tree the WebSocket carries.
 *  - COMMAND (write): one UTF-8 JSON command per message.
 *  - ACK (notify, optional): one UTF-8 JSON acknowledgement per command ([BleCommands]).
 * A message is larger than one ATT payload, so both directions cut it into frames ([BleFraming]).
 */
object BleContract {
    /** Primary service a HYDRA-UMC device advertises. */
    val SERVICE_UUID: UUID = UUID.fromString("48594452-0001-4000-8000-554d43000001")
    /** Robot state, notified by the device. */
    val STATE_CHAR_UUID: UUID = UUID.fromString("48594452-0002-4000-8000-554d43000001")
    /** Commands written by the app. */
    val COMMAND_CHAR_UUID: UUID = UUID.fromString("48594452-0003-4000-8000-554d43000001")
    /** Acknowledgements of the commands, notified by the device (absent on a device that takes no commands). */
    val ACK_CHAR_UUID: UUID = UUID.fromString("48594452-0004-4000-8000-554d43000001")
    /** Client Characteristic Configuration descriptor (Bluetooth SIG). */
    val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    /** MTU of a link nobody negotiated. */
    const val DEFAULT_MTU = 23
    /** MTU the app asks for; the device may grant less. */
    const val PREFERRED_MTU = 247
    /** ATT header bytes inside an MTU. */
    const val ATT_OVERHEAD = 3
    /** Largest message accepted in either direction. */
    const val MAX_MESSAGE_BYTES = 64 * 1024
}

/** What went wrong, without text: the app turns it into a message in the language of the phone. */
enum class BleErrorKind(val permanent: Boolean) {
    BLUETOOTH_UNAVAILABLE(true),
    BLUETOOTH_OFF(true),
    PERMISSION_DENIED(true),
    LOCATION_OFF(true),
    INVALID_ADDRESS(true),
    SCANNER_UNAVAILABLE(true),
    SCAN_FAILED(false),
    CONNECT_FAILED(false),
    SERVICE_DISCOVERY_FAILED(false),
    /** The device answers but has no HYDRA service: it is not a HYDRA-UMC device. */
    NOT_A_HYDRA_DEVICE(true),
    /** The service exists but a characteristic is missing or lacks the needed property. */
    CHARACTERISTIC_MISSING(true),
    NOTIFICATIONS_FAILED(true),
    WRITE_FAILED(false),
    NOT_CONNECTED(false),
    MESSAGE_TOO_LARGE(false),
    MALFORMED_FRAME(false),
    RECONNECT_GAVE_UP(true),
}

/** An error plus the GATT/scan status code the platform gave, when there was one. */
data class BleError(val kind: BleErrorKind, val status: Int? = null)

/** Thrown by a [BleGattConnector] when a link cannot even be started. */
class BleException(val error: BleError, cause: Throwable? = null) :
    Exception("${error.kind} ${error.status ?: ""}".trim(), cause)

/**
 * Framing of a message over a link whose payload is MTU - 3 bytes.
 *
 * Every frame starts with one header byte: bit 0 = first frame, bit 1 = last frame, bits 4-7 =
 * sequence number (0 on the first frame, +1 per frame, modulo 16). A message of one frame has both
 * bits set. The rest of the frame is a slice of the UTF-8 message.
 */
object BleFraming {
    const val FIRST = 0x01
    const val LAST = 0x02

    /** Bytes of message that fit in one frame for this [mtu]. */
    fun chunkSize(mtu: Int): Int = (mtu - BleContract.ATT_OVERHEAD - 1).coerceAtLeast(1)

    /** Cuts [message] into frames; an empty or too large message gives an empty list. */
    fun encode(message: ByteArray, mtu: Int): List<ByteArray> {
        if (message.isEmpty() || message.size > BleContract.MAX_MESSAGE_BYTES) return emptyList()
        val chunk = chunkSize(mtu)
        val count = (message.size + chunk - 1) / chunk
        return List(count) { i ->
            val from = i * chunk
            val to = minOf(from + chunk, message.size)
            var header = (i and 0x0F) shl 4
            if (i == 0) header = header or FIRST
            if (i == count - 1) header = header or LAST
            ByteArray(1 + to - from).also { frame ->
                frame[0] = header.toByte()
                message.copyInto(frame, 1, from, to)
            }
        }
    }
}

/** Puts frames back together; one instance per link. */
class BleReassembler {
    /** Result of feeding one frame. */
    sealed class Result {
        /** The message is not complete yet. */
        object Pending : Result()
        /** The message is complete. */
        class Message(val bytes: ByteArray) : Result()
        /** The frame did not fit; the partial message was dropped. */
        object Malformed : Result()
    }

    private var buffer: ByteArrayOutputStream? = null
    private var nextSeq = 0

    /** Forgets a partial message (call when the link drops). */
    fun reset() {
        buffer = null
        nextSeq = 0
    }

    fun push(frame: ByteArray): Result {
        if (frame.isEmpty()) return Result.Malformed
        val header = frame[0].toInt() and 0xFF
        val first = header and BleFraming.FIRST != 0
        val last = header and BleFraming.LAST != 0
        val seq = header shr 4
        if (first) {
            buffer = ByteArrayOutputStream()
            nextSeq = 0
        }
        val out = buffer
        if (out == null || seq != nextSeq) {
            reset()
            return Result.Malformed
        }
        if (out.size() + frame.size - 1 > BleContract.MAX_MESSAGE_BYTES) {
            reset()
            return Result.Malformed
        }
        out.write(frame, 1, frame.size - 1)
        nextSeq = (nextSeq + 1) and 0x0F
        if (!last) return Result.Pending
        val bytes = out.toByteArray()
        reset()
        return Result.Message(bytes)
    }
}

/** How a dropped link is retried: a growing delay, then give up. */
data class BleReconnectPolicy(val maxAttempts: Int = 5, val baseDelayMs: Long = 1_000, val maxDelayMs: Long = 16_000) {
    /** True while one more attempt is allowed after [attemptsDone]. */
    fun shouldRetry(attemptsDone: Int): Boolean = attemptsDone < maxAttempts

    /** Delay before attempt number [attempt] (1 = the first retry): base, 2x, 4x... up to the cap. */
    fun delayMs(attempt: Int): Long {
        val shift = (attempt - 1).coerceIn(0, 30)
        return minOf(baseDelayMs shl shift, maxDelayMs)
    }

    companion object {
        val DEFAULT = BleReconnectPolicy()
    }
}
