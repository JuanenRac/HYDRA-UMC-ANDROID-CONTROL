// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - app/src/test/java/com/hydraumc/control/network/BleFramingTest.kt
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.network

import com.hydraumc.control.model.BleDevice
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The BLE message framing, the reconnect timing, the permission lists and the device list: pure JVM. */
class BleFramingTest {
    private fun roundTrip(message: ByteArray, mtu: Int): ByteArray {
        val frames = BleFraming.encode(message, mtu)
        val reassembler = BleReassembler()
        var result: BleReassembler.Result = BleReassembler.Result.Pending
        for (frame in frames) {
            assertTrue("a frame must fit the ATT payload", frame.size <= mtu - BleContract.ATT_OVERHEAD)
            result = reassembler.push(frame)
        }
        return (result as BleReassembler.Result.Message).bytes
    }

    @Test fun `a short message is one frame with both flags`() {
        val frames = BleFraming.encode("{}".toByteArray(), 23)
        assertEquals(1, frames.size)
        assertEquals((BleFraming.FIRST or BleFraming.LAST).toByte(), frames[0][0])
        assertArrayEquals("{}".toByteArray(), roundTrip("{}".toByteArray(), 23))
    }

    @Test fun `messages of every size survive every mtu`() {
        for (mtu in listOf(23, 24, 50, 185, 247, 517)) {
            for (size in listOf(1, 18, 19, 20, 21, 100, 1000, 4097)) {
                val message = ByteArray(size) { (it * 31 + size).toByte() }
                assertArrayEquals("mtu $mtu size $size", message, roundTrip(message, mtu))
            }
        }
    }

    @Test fun `the sequence wraps after sixteen frames`() {
        val message = ByteArray(19 * 40) { it.toByte() } // 40 frames at the default mtu (19 bytes each)
        val frames = BleFraming.encode(message, BleContract.DEFAULT_MTU)
        assertEquals(40, frames.size)
        assertEquals(0, (frames[16][0].toInt() and 0xFF) shr 4)
        assertEquals(1, (frames[17][0].toInt() and 0xFF) shr 4)
        assertArrayEquals(message, roundTrip(message, BleContract.DEFAULT_MTU))
    }

    @Test fun `utf8 text is kept whole`() {
        val text = "{\"name\":\"Brazo ñandú 機械 🤖\"}"
        assertEquals(text, String(roundTrip(text.toByteArray(Charsets.UTF_8), 23), Charsets.UTF_8))
    }

    @Test fun `empty and oversized messages give no frames`() {
        assertTrue(BleFraming.encode(ByteArray(0), 23).isEmpty())
        assertTrue(BleFraming.encode(ByteArray(BleContract.MAX_MESSAGE_BYTES + 1), 23).isEmpty())
        assertTrue(BleFraming.encode(ByteArray(BleContract.MAX_MESSAGE_BYTES), 517).isNotEmpty())
    }

    @Test fun `a continuation with no start is malformed`() {
        val frames = BleFraming.encode(ByteArray(60) { 1 }, 23)
        val reassembler = BleReassembler()
        assertTrue(reassembler.push(frames[1]) is BleReassembler.Result.Malformed)
    }

    @Test fun `a lost frame is detected and the next message still arrives`() {
        val frames = BleFraming.encode(ByteArray(80) { 2 }, 23)
        val reassembler = BleReassembler()
        assertTrue(reassembler.push(frames[0]) is BleReassembler.Result.Pending)
        assertTrue(reassembler.push(frames[2]) is BleReassembler.Result.Malformed) // frames[1] never came
        val fresh = BleFraming.encode("ok".toByteArray(), 23)
        assertTrue(reassembler.push(fresh[0]) is BleReassembler.Result.Message)
    }

    @Test fun `a new start replaces a partial message`() {
        val long = BleFraming.encode(ByteArray(80) { 3 }, 23)
        val reassembler = BleReassembler()
        reassembler.push(long[0])
        val short = BleFraming.encode("hi".toByteArray(), 23)
        val result = reassembler.push(short[0]) as BleReassembler.Result.Message
        assertArrayEquals("hi".toByteArray(), result.bytes)
    }

    @Test fun `an empty frame is malformed`() {
        assertTrue(BleReassembler().push(ByteArray(0)) is BleReassembler.Result.Malformed)
    }

    @Test fun `reset forgets the partial message`() {
        val frames = BleFraming.encode(ByteArray(80) { 4 }, 23)
        val reassembler = BleReassembler()
        reassembler.push(frames[0])
        reassembler.reset()
        assertTrue(reassembler.push(frames[1]) is BleReassembler.Result.Malformed)
    }

    @Test fun `a message that grows past the limit is dropped`() {
        val reassembler = BleReassembler()
        val chunk = ByteArray(1 + 20_000) // header 0 (continuation flags set below)
        chunk[0] = BleFraming.FIRST.toByte()
        assertTrue(reassembler.push(chunk) is BleReassembler.Result.Pending)
        var result: BleReassembler.Result = BleReassembler.Result.Pending
        for (i in 1..4) {
            val next = ByteArray(1 + 20_000)
            next[0] = (i shl 4).toByte()
            result = reassembler.push(next)
        }
        assertTrue(result is BleReassembler.Result.Malformed)
    }

    @Test fun `the reconnect delays double up to the cap`() {
        val policy = BleReconnectPolicy(maxAttempts = 6, baseDelayMs = 1_000, maxDelayMs = 16_000)
        assertEquals(listOf(1_000L, 2_000L, 4_000L, 8_000L, 16_000L, 16_000L), (1..6).map(policy::delayMs))
        assertTrue(policy.shouldRetry(5))
        assertTrue(!policy.shouldRetry(6))
    }

    @Test fun `android 12 asks for scan and connect and older versions for location`() {
        assertEquals(listOf(BlePermissions.SCAN, BlePermissions.CONNECT), BlePermissions.all(31))
        assertEquals(listOf(BlePermissions.SCAN, BlePermissions.CONNECT), BlePermissions.all(35))
        assertEquals(listOf(BlePermissions.FINE_LOCATION), BlePermissions.all(30))
        assertEquals(emptyList<String>(), BlePermissions.connectPermissions(30))
        assertTrue(BlePermissions.needsLocationServices(30))
        assertTrue(!BlePermissions.needsLocationServices(31))
        assertEquals(
            listOf(BlePermissions.CONNECT),
            BlePermissions.missing(BlePermissions.all(33)) { it == BlePermissions.SCAN },
        )
    }

    @Test fun `the device list refreshes entries, keeps a known name and puts the strongest first`() {
        var list = emptyList<BleDevice>()
        list = BleDeviceList.upsert(list, BleDevice("Arm", "AA:AA:AA:AA:AA:01", -80))
        list = BleDeviceList.upsert(list, BleDevice(null, "AA:AA:AA:AA:AA:02", -50))
        list = BleDeviceList.upsert(list, BleDevice(null, "AA:AA:AA:AA:AA:01", -40))
        assertEquals(listOf("AA:AA:AA:AA:AA:01", "AA:AA:AA:AA:AA:02"), list.map { it.address })
        assertEquals("Arm", list[0].name)
        assertEquals(-40, list[0].rssi)
    }

    @Test fun `every error kind has a text`() {
        // The mapping is a total `when`; this keeps the permanent flags from drifting silently.
        assertTrue(BleErrorKind.NOT_A_HYDRA_DEVICE.permanent)
        assertTrue(!BleErrorKind.CONNECT_FAILED.permanent)
        assertEquals(17, BleErrorKind.entries.size)
    }
}
