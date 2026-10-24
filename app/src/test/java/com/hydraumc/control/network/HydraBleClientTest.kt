// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - app/src/test/java/com/hydraumc/control/network/HydraBleClientTest.kt
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.network

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The whole BLE client against a fake GATT: the device is simulated, no phone or radio is involved.
 * Nothing here proves the Android GATT stack (AndroidBleGatt.kt) behaves like the fake.
 */
@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class HydraBleClientTest {

    /** A scripted GATT link; tests push events through [events]. */
    private class FakeLink(val events: BleGattEvents) : BleGattLink {
        var mtuRequests = mutableListOf<Int>()
        var mtuAccepted = true
        var discoverCalls = 0
        var lookup = BleContractLookup.FOUND
        var notificationsRequested = 0
        var notificationsAccepted = true
        var hasAck = false
        var ackNotificationsRequested = 0
        var ackNotificationsAccepted = true
        val writes = mutableListOf<ByteArray>()
        var writeAccepted = true
        var closed = 0

        override fun requestMtu(mtu: Int): Boolean { mtuRequests += mtu; return mtuAccepted }
        override fun discoverServices(): Boolean { discoverCalls++; return true }
        override fun findContract() = lookup
        override fun enableStateNotifications(): Boolean { notificationsRequested++; return notificationsAccepted }
        override fun hasAckCharacteristic() = hasAck
        override fun enableAckNotifications(): Boolean { ackNotificationsRequested++; return ackNotificationsAccepted }
        override fun writeCommand(frame: ByteArray): Boolean { writes += frame; return writeAccepted }
        override fun close() { closed++ }
    }

    private class Harness(
        val scope: TestScope,
        policy: BleReconnectPolicy = BleReconnectPolicy(3, 1_000, 8_000),
        val deviceTakesCommands: Boolean = false,
        ackTimeoutMs: Long = BleCommands.DEFAULT_ACK_TIMEOUT_MS,
    ) {
        val links = mutableListOf<FakeLink>()
        var openError: BleException? = null
        val statuses = mutableListOf<WsStatus>()
        val settings = mutableListOf<String>()
        val errors = mutableListOf<BleError>()
        val link get() = links.last()

        val client = HydraBleClient(
            connector = { _, events ->
                openError?.let { throw it }
                FakeLink(events).also { it.hasAck = deviceTakesCommands; links += it }
            },
            deviceAddress = "AA:BB:CC:DD:EE:FF",
            scope = scope,
            onStatus = { statuses += it },
            onSettings = { settings += it },
            onError = { errors += it },
            reconnectPolicy = policy,
            ackTimeoutMs = ackTimeoutMs,
        )

        /** Runs the link up to the ready state with the given MTU. */
        fun bringUp(mtu: Int = 185) {
            client.connect()
            scope.runCurrent()
            link.events.onConnectionState(true, 0); scope.runCurrent()
            link.events.onMtu(mtu, 0); scope.runCurrent()
            link.events.onServicesDiscovered(0); scope.runCurrent()
            link.events.onNotificationsEnabled(0); scope.runCurrent()
            if (deviceTakesCommands) { link.events.onAckNotificationsEnabled(0); scope.runCurrent() }
        }

        /** Notifies a whole acknowledgement message (framed like the peripheral does). */
        fun ack(json: String, mtu: Int = 185) {
            for (frame in BleFraming.encode(json.toByteArray(), mtu)) { link.events.onAckFrame(frame); scope.runCurrent() }
        }
    }

    @Test fun `connect walks mtu, discovery and notifications and only then reports connected`() = runTest {
        val h = Harness(this)
        h.client.connect(); runCurrent()
        assertEquals(listOf(WsStatus.CONNECTING), h.statuses)
        h.link.events.onConnectionState(true, 0); runCurrent()
        assertEquals(listOf(BleContract.PREFERRED_MTU), h.link.mtuRequests)
        assertEquals(0, h.link.discoverCalls)
        h.link.events.onMtu(185, 0); runCurrent()
        assertEquals(1, h.link.discoverCalls)
        h.link.events.onServicesDiscovered(0); runCurrent()
        assertEquals(1, h.link.notificationsRequested)
        assertEquals(listOf(WsStatus.CONNECTING), h.statuses)
        h.link.events.onNotificationsEnabled(0); runCurrent()
        assertEquals(listOf(WsStatus.CONNECTING, WsStatus.CONNECTED), h.statuses)
        assertTrue(h.client.isReady)
        assertTrue(h.errors.isEmpty())
    }

    @Test fun `a second connect while one is running opens no second link`() = runTest {
        val h = Harness(this)
        h.client.connect(); h.client.connect(); runCurrent()
        assertEquals(1, h.links.size)
    }

    @Test fun `when the mtu cannot be requested discovery starts at once with the default mtu`() = runTest {
        val h = Harness(this)
        h.client.connect(); runCurrent()
        h.link.mtuAccepted = false
        h.link.events.onConnectionState(true, 0); runCurrent()
        assertEquals(1, h.link.discoverCalls)
    }

    @Test fun `state frames are reassembled into one settings text`() = runTest {
        val h = Harness(this)
        h.bringUp()
        val json = "{\"robots\":[" + "{\"id\":1,\"name\":\"arm\"},".repeat(30) + "{\"id\":2}]}"
        for (frame in BleFraming.encode(json.toByteArray(), 185)) {
            h.link.events.onStateFrame(frame); runCurrent()
        }
        assertEquals(listOf(json), h.settings)
        assertTrue(BleFraming.encode(json.toByteArray(), 185).size > 1)
    }

    @Test fun `a damaged state frame is reported and the next message still works`() = runTest {
        val h = Harness(this)
        h.bringUp()
        h.link.events.onStateFrame(byteArrayOf(0x20, 1, 2)); runCurrent() // sequence 2 with no start
        assertEquals(listOf(BleError(BleErrorKind.MALFORMED_FRAME)), h.errors)
        h.link.events.onStateFrame(BleFraming.encode("{}".toByteArray(), 185)[0]); runCurrent()
        assertEquals(listOf("{}"), h.settings)
    }

    @Test fun `commands are framed with the negotiated mtu and written one at a time`() = runTest {
        val h = Harness(this)
        h.bringUp(mtu = 50)
        val command = "{\"robotId\":1,\"command\":\"jog\",\"params\":{\"axis\":\"x\",\"amount\":1.5,\"pad\":\"" + "p".repeat(100) + "\"}}"
        assertTrue(h.client.send(command))
        runCurrent()
        val expected = BleFraming.encode(command.toByteArray(), 50)
        assertTrue(expected.size > 2)
        assertEquals("the second write waits for the first to finish", 1, h.link.writes.size)
        assertArrayEquals(expected[0], h.link.writes[0])
        for (i in 1 until expected.size) {
            h.link.events.onWriteDone(0); runCurrent()
            assertEquals(i + 1, h.link.writes.size)
            assertArrayEquals(expected[i], h.link.writes[i])
        }
        h.link.events.onWriteDone(0); runCurrent()
        assertEquals(expected.size, h.link.writes.size)
        assertTrue(h.errors.isEmpty())
    }

    @Test fun `a refused write drops the rest of that message and reports the status`() = runTest {
        val h = Harness(this)
        h.bringUp(mtu = 23)
        h.client.send("x".repeat(60)); runCurrent()
        h.link.events.onWriteDone(133); runCurrent()
        assertEquals(listOf(BleError(BleErrorKind.WRITE_FAILED, 133)), h.errors)
        assertEquals("nothing else is written after the failure", 1, h.link.writes.size)
        // The link is still usable.
        assertTrue(h.client.send("{}")); runCurrent()
        assertEquals(2, h.link.writes.size)
    }

    @Test fun `a write the platform will not start is reported`() = runTest {
        val h = Harness(this)
        h.bringUp()
        h.link.writeAccepted = false
        h.client.send("{}"); runCurrent()
        assertEquals(listOf(BleError(BleErrorKind.WRITE_FAILED)), h.errors)
    }

    @Test fun `send before the link is ready says not connected`() = runTest {
        val h = Harness(this)
        assertFalse(h.client.send("{}"))
        h.client.connect(); runCurrent()
        assertFalse(h.client.send("{}"))
        assertEquals(listOf(BleError(BleErrorKind.NOT_CONNECTED), BleError(BleErrorKind.NOT_CONNECTED)), h.errors)
    }

    @Test fun `empty and oversized commands are not sent`() = runTest {
        val h = Harness(this)
        h.bringUp()
        assertFalse(h.client.send(""))
        assertFalse(h.client.send("x".repeat(BleContract.MAX_MESSAGE_BYTES + 1)))
        assertEquals(listOf(BleError(BleErrorKind.MESSAGE_TOO_LARGE)), h.errors)
        assertTrue(h.link.writes.isEmpty())
    }

    @Test fun `a device without the HYDRA service is refused and not retried`() = runTest {
        val h = Harness(this)
        h.client.connect(); runCurrent()
        h.link.lookup = BleContractLookup.NO_SERVICE
        h.link.events.onConnectionState(true, 0); runCurrent()
        h.link.events.onMtu(185, 0); runCurrent()
        h.link.events.onServicesDiscovered(0); runCurrent()
        assertEquals(listOf(BleError(BleErrorKind.NOT_A_HYDRA_DEVICE)), h.errors)
        assertEquals(WsStatus.DISCONNECTED, h.statuses.last())
        assertEquals(1, h.link.closed)
        advanceTimeBy(60_000); runCurrent()
        assertEquals("no retry for a permanent error", 1, h.links.size)
    }

    @Test fun `a missing characteristic is its own error`() = runTest {
        val h = Harness(this)
        h.client.connect(); runCurrent()
        h.link.lookup = BleContractLookup.CHARACTERISTIC_MISSING
        h.link.events.onConnectionState(true, 0); runCurrent()
        h.link.events.onMtu(185, 0); runCurrent()
        h.link.events.onServicesDiscovered(0); runCurrent()
        assertEquals(listOf(BleError(BleErrorKind.CHARACTERISTIC_MISSING)), h.errors)
    }

    @Test fun `notifications that cannot be enabled are an error`() = runTest {
        val h = Harness(this)
        h.client.connect(); runCurrent()
        h.link.events.onConnectionState(true, 0); runCurrent()
        h.link.events.onMtu(185, 0); runCurrent()
        h.link.events.onServicesDiscovered(0); runCurrent()
        h.link.events.onNotificationsEnabled(5); runCurrent() // insufficient authentication
        assertEquals(listOf(BleError(BleErrorKind.NOTIFICATIONS_FAILED, 5)), h.errors)
        assertFalse(h.client.isReady)
    }

    @Test fun `a link that cannot be started reports the platform error once and stays down`() = runTest {
        val h = Harness(this)
        h.openError = BleException(BleError(BleErrorKind.PERMISSION_DENIED))
        h.client.connect(); runCurrent()
        assertEquals(listOf(BleError(BleErrorKind.PERMISSION_DENIED)), h.errors)
        assertEquals(listOf(WsStatus.CONNECTING, WsStatus.DISCONNECTED), h.statuses)
        advanceTimeBy(60_000); runCurrent()
        assertTrue(h.links.isEmpty())
        // A later explicit connect tries again.
        h.openError = null
        h.client.connect(); runCurrent()
        assertEquals(1, h.links.size)
    }

    @Test fun `a dropped link is retried with growing delays and gives up after the last attempt`() = runTest {
        val h = Harness(this)
        h.bringUp()
        h.link.events.onConnectionState(false, 8); runCurrent() // supervision timeout
        assertEquals(1, h.links[0].closed)
        assertEquals(WsStatus.CONNECTING, h.statuses.last())
        advanceTimeBy(999); runCurrent()
        assertEquals(1, h.links.size)
        advanceTimeBy(2); runCurrent()
        assertEquals("first retry after 1 s", 2, h.links.size)

        h.link.events.onConnectionState(false, 133); runCurrent()
        advanceTimeBy(1_999); runCurrent()
        assertEquals(2, h.links.size)
        advanceTimeBy(2); runCurrent()
        assertEquals("second retry after 2 s", 3, h.links.size)

        h.link.events.onConnectionState(false, 133); runCurrent()
        advanceTimeBy(4_001); runCurrent()
        assertEquals("third retry after 4 s", 4, h.links.size)

        h.link.events.onConnectionState(false, 133); runCurrent()
        assertEquals(WsStatus.DISCONNECTED, h.statuses.last())
        assertEquals(listOf(BleError(BleErrorKind.RECONNECT_GAVE_UP, 133)), h.errors)
        advanceTimeBy(60_000); runCurrent()
        assertEquals("no attempt after giving up", 4, h.links.size)
    }

    @Test fun `a link that comes back resets the retry budget`() = runTest {
        val h = Harness(this)
        h.bringUp()
        repeat(2) { round ->
            h.link.events.onConnectionState(false, 8); runCurrent()
            advanceTimeBy(10_000); runCurrent()
            h.link.events.onConnectionState(true, 0); runCurrent()
            h.link.events.onMtu(185, 0); runCurrent()
            h.link.events.onServicesDiscovered(0); runCurrent()
            h.link.events.onNotificationsEnabled(0); runCurrent()
            assertTrue("round $round ready again", h.client.isReady)
        }
        assertTrue(h.errors.isEmpty())
        assertEquals(WsStatus.CONNECTED, h.statuses.last())
    }

    @Test fun `a partial message does not leak into the next connection`() = runTest {
        val h = Harness(this)
        h.bringUp(mtu = 23)
        val frames = BleFraming.encode("{\"a\":\"${"z".repeat(60)}\"}".toByteArray(), 23)
        h.link.events.onStateFrame(frames[0]); runCurrent()
        h.link.events.onConnectionState(false, 8); runCurrent()
        advanceTimeBy(1_001); runCurrent()
        h.link.events.onConnectionState(true, 0); runCurrent()
        h.link.events.onMtu(23, 0); runCurrent()
        h.link.events.onServicesDiscovered(0); runCurrent()
        h.link.events.onNotificationsEnabled(0); runCurrent()
        h.link.events.onStateFrame(frames[1]); runCurrent()
        assertEquals(listOf(BleError(BleErrorKind.MALFORMED_FRAME)), h.errors)
        assertTrue(h.settings.isEmpty())
    }

    @Test fun `disconnect closes the link, stops the retry and ignores late events`() = runTest {
        val h = Harness(this)
        h.bringUp()
        val first = h.link
        h.link.events.onConnectionState(false, 8); runCurrent() // a retry is now scheduled
        h.client.disconnect(); runCurrent()
        assertEquals(WsStatus.DISCONNECTED, h.statuses.last())
        advanceTimeBy(60_000); runCurrent()
        assertEquals("the scheduled retry never ran", 1, h.links.size)
        // Events of a link that is already gone change nothing.
        first.events.onNotificationsEnabled(0); first.events.onStateFrame(BleFraming.encode("{}".toByteArray(), 23)[0]); runCurrent()
        assertTrue(h.settings.isEmpty())
        assertFalse(h.client.isReady)
    }

    @Test fun `disconnect of a ready link closes it`() = runTest {
        val h = Harness(this)
        h.bringUp()
        h.client.disconnect(); runCurrent()
        assertEquals(1, h.link.closed)
        assertFalse(h.client.send("{}"))
    }

    @Test fun `service discovery failing is retried like a drop`() = runTest {
        val h = Harness(this)
        h.client.connect(); runCurrent()
        h.link.events.onConnectionState(true, 0); runCurrent()
        h.link.events.onMtu(185, 0); runCurrent()
        h.link.events.onServicesDiscovered(129); runCurrent()
        assertEquals(1, h.links[0].closed)
        advanceTimeBy(1_001); runCurrent()
        assertEquals(2, h.links.size)
    }

    // ---- commands over Bluetooth and their acknowledgements -------------------------------------------------------

    private fun request(command: String = "pause", robotId: Int = 1) =
        BleCommandRequest(robotId = robotId, command = command, controllerId = "phone-1", token = "tok-123")

    /** The command message the client wrote, put back together. */
    private fun writtenCommand(h: Harness): org.json.JSONObject {
        val reassembler = BleReassembler()
        var message: ByteArray? = null
        for (frame in h.link.writes) {
            val result = reassembler.push(frame)
            if (result is BleReassembler.Result.Message) message = result.bytes
        }
        return org.json.JSONObject(String(message!!, Charsets.UTF_8))
    }

    private fun ackJson(id: String?, ok: Boolean, status: Int, code: String, error: String? = null): String {
        val json = org.json.JSONObject().put("id", id ?: org.json.JSONObject.NULL).put("ok", ok).put("status", status).put("code", code)
        if (error != null) json.put("error", error)
        return json.toString()
    }

    @Test fun `a device that takes commands is reported connected only after its acknowledgements are on`() = runTest {
        val h = Harness(this, deviceTakesCommands = true)
        h.client.connect(); runCurrent()
        h.link.events.onConnectionState(true, 0); runCurrent()
        h.link.events.onMtu(185, 0); runCurrent()
        h.link.events.onServicesDiscovered(0); runCurrent()
        h.link.events.onNotificationsEnabled(0); runCurrent()
        assertEquals(1, h.link.ackNotificationsRequested)
        assertEquals(listOf(WsStatus.CONNECTING), h.statuses)
        assertFalse(h.client.canSendCommands)
        h.link.events.onAckNotificationsEnabled(0); runCurrent()
        assertEquals(listOf(WsStatus.CONNECTING, WsStatus.CONNECTED), h.statuses)
        assertTrue(h.client.isReady && h.client.canSendCommands)
    }

    @Test fun `a device without the ACK characteristic shows its state but takes no commands`() = runTest {
        val h = Harness(this)
        h.bringUp()
        assertTrue(h.client.isReady)
        assertFalse(h.client.canSendCommands)
        val outcomes = mutableListOf<BleCommandOutcome>()
        h.client.sendCommand(request()) { outcomes += it }
        assertEquals(listOf<BleCommandOutcome>(BleCommandOutcome.NotAvailable), outcomes)
        assertTrue(h.link.writes.isEmpty())
    }

    @Test fun `acknowledgements that cannot be turned on leave the state working and the commands unavailable`() = runTest {
        val h = Harness(this, deviceTakesCommands = true)
        h.client.connect(); runCurrent()
        h.link.events.onConnectionState(true, 0); runCurrent()
        h.link.events.onMtu(185, 0); runCurrent()
        h.link.events.onServicesDiscovered(0); runCurrent()
        h.link.events.onNotificationsEnabled(0); runCurrent()
        h.link.events.onAckNotificationsEnabled(5); runCurrent()
        assertTrue(h.client.isReady)
        assertFalse(h.client.canSendCommands)
        assertTrue(h.errors.isEmpty())
        assertEquals(WsStatus.CONNECTED, h.statuses.last())
    }

    @Test fun `a command goes out framed with its id and token and an acknowledgement answers it`() = runTest {
        val h = Harness(this, deviceTakesCommands = true)
        h.bringUp(mtu = 23) // small frames: the command is cut into several
        val outcomes = mutableListOf<BleCommandOutcome>()
        h.client.sendCommand(request()) { outcomes += it }
        runCurrent()
        // one frame is written at a time: the platform answers each write
        repeat(40) { h.link.events.onWriteDone(0); runCurrent() }
        assertTrue(h.link.writes.size > 1)
        val body = writtenCommand(h)
        assertEquals(1, body.getInt("robotId"))
        assertEquals("pause", body.getString("command"))
        assertEquals("tok-123", body.getString("token"))
        assertEquals("phone-1", body.getString("controllerId"))
        val id = body.getString("id")
        assertTrue(id.isNotEmpty() && id.length <= BleCommands.MAX_ID_CHARS)
        assertTrue("nothing is reported before the acknowledgement", outcomes.isEmpty())
        h.ack(ackJson(id, true, 200, "ok"))
        assertEquals(1, outcomes.size)
        val ack = (outcomes[0] as BleCommandOutcome.Acknowledged).ack
        assertTrue(ack.ok && ack.status == 200 && ack.code == "ok")
    }

    @Test fun `a refusal of the server is an acknowledgement that says not ok`() = runTest {
        val h = Harness(this, deviceTakesCommands = true)
        h.bringUp()
        val outcomes = mutableListOf<BleCommandOutcome>()
        h.client.sendCommand(request("stop")) { outcomes += it }
        runCurrent()
        val id = writtenCommand(h).getString("id")
        h.ack(ackJson(id, false, 409, "claimed", "reserved by ana"))
        val ack = (outcomes.single() as BleCommandOutcome.Acknowledged).ack
        assertFalse(ack.ok)
        assertEquals(listOf<Any?>(409, "claimed", "reserved by ana"), listOf(ack.status, ack.code, ack.error))
    }

    @Test fun `an acknowledgement of another command is ignored and the right one still arrives`() = runTest {
        val h = Harness(this, deviceTakesCommands = true)
        h.bringUp()
        val outcomes = mutableListOf<BleCommandOutcome>()
        h.client.sendCommand(request()) { outcomes += it }
        runCurrent()
        val id = writtenCommand(h).getString("id")
        h.ack(ackJson("someone-else", true, 200, "ok"))
        h.ack(ackJson(null, false, 0, "damaged-message"))
        assertTrue(outcomes.isEmpty())
        h.ack(ackJson(id, true, 200, "ok"))
        assertEquals(1, outcomes.size)
    }

    @Test fun `no acknowledgement in time means the command was not delivered, and a late one changes nothing`() = runTest {
        val h = Harness(this, deviceTakesCommands = true, ackTimeoutMs = 3_000)
        h.bringUp()
        val outcomes = mutableListOf<BleCommandOutcome>()
        h.client.sendCommand(request("stop")) { outcomes += it }
        runCurrent()
        val id = writtenCommand(h).getString("id")
        advanceTimeBy(2_999); runCurrent()
        assertTrue(outcomes.isEmpty())
        advanceTimeBy(2); runCurrent()
        assertEquals(listOf<BleCommandOutcome>(BleCommandOutcome.NoAnswer), outcomes)
        h.ack(ackJson(id, true, 200, "ok"))
        assertEquals(1, outcomes.size)
    }

    @Test fun `a link that drops fails the commands waiting for an acknowledgement at once`() = runTest {
        val h = Harness(this, deviceTakesCommands = true)
        h.bringUp()
        val outcomes = mutableListOf<BleCommandOutcome>()
        h.client.sendCommand(request()) { outcomes += it }
        h.client.sendCommand(request("jog")) { outcomes += it }
        runCurrent()
        h.link.events.onConnectionState(false, 8); runCurrent()
        assertEquals(listOf<BleCommandOutcome>(BleCommandOutcome.NoAnswer, BleCommandOutcome.NoAnswer), outcomes)
        assertFalse(h.client.canSendCommands)
    }

    @Test fun `a damaged acknowledgement frame is reported and the next acknowledgement still works`() = runTest {
        val h = Harness(this, deviceTakesCommands = true)
        h.bringUp()
        val outcomes = mutableListOf<BleCommandOutcome>()
        h.client.sendCommand(request()) { outcomes += it }
        runCurrent()
        val id = writtenCommand(h).getString("id")
        h.link.events.onAckFrame(byteArrayOf(0x12, 65)); runCurrent() // a continuation with no start
        assertEquals(listOf(BleErrorKind.MALFORMED_FRAME), h.errors.map { it.kind })
        h.ack(ackJson(id, true, 200, "ok"))
        assertEquals(1, outcomes.size)
    }

    @Test fun `a command that does not fit in one message is not sent`() = runTest {
        val h = Harness(this, deviceTakesCommands = true)
        h.bringUp()
        val outcomes = mutableListOf<BleCommandOutcome>()
        val huge = org.json.JSONObject().put("blob", "x".repeat(BleContract.MAX_MESSAGE_BYTES))
        h.client.sendCommand(BleCommandRequest(1, "speed", huge, "phone-1", "tok")) { outcomes += it }
        assertEquals(listOf<BleCommandOutcome>(BleCommandOutcome.TooLarge), outcomes)
        assertTrue(h.link.writes.isEmpty())
    }
}
