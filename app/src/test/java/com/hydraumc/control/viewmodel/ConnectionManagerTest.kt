// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - test/viewmodel/ConnectionManagerTest.kt
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.viewmodel

import com.hydraumc.control.network.HydraApiException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The connect sequence and the metrics poll: it reads while it should, skips a failed read, stops when it should not run and restarts without doubling. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ConnectionManagerTest {
    private val body = JSONObject("""{"cpu_load":12,"memory_usage":34,"temp":55.5,"uptime":99}""")

    @Test
    fun `reads the metrics now and at every interval while it should poll`() = runTest {
        val seen = mutableListOf<SystemMetrics>()
        val manager = ConnectionManager(scope = backgroundScope, shouldPoll = { true }, onMetrics = { seen += it })
        manager.startMetrics { body }
        runCurrent()
        assertEquals(1, seen.size)
        assertEquals(SystemMetrics(cpuLoad = 12, memoryUsage = 34, temp = 55.5, uptime = 99), seen[0])
        assertTrue(manager.isPolling)
        advanceTimeBy(5001)
        runCurrent()
        assertEquals(2, seen.size)
        manager.stopMetrics()
        assertFalse(manager.isPolling)
    }

    @Test
    fun `a failed read is skipped and the next one still happens`() = runTest {
        val seen = mutableListOf<SystemMetrics>()
        var calls = 0
        val manager = ConnectionManager(scope = backgroundScope, shouldPoll = { true }, onMetrics = { seen += it })
        manager.startMetrics {
            calls++
            if (calls == 1) error("network down") else body
        }
        runCurrent()
        assertEquals(0, seen.size)
        advanceTimeBy(5001)
        runCurrent()
        assertEquals(1, seen.size)
        manager.stopMetrics()
    }

    @Test
    fun `it ends by itself when it should no longer poll`() = runTest {
        var poll = true
        val seen = mutableListOf<SystemMetrics>()
        val manager = ConnectionManager(scope = backgroundScope, shouldPoll = { poll }, onMetrics = { seen += it })
        manager.startMetrics { body }
        runCurrent()
        poll = false
        advanceTimeBy(5001)
        runCurrent()
        assertEquals(1, seen.size)
        assertFalse(manager.isPolling)
    }

    @Test
    fun `starting again cancels the previous loop instead of doubling it`() = runTest {
        val seen = mutableListOf<SystemMetrics>()
        val manager = ConnectionManager(scope = backgroundScope, shouldPoll = { true }, onMetrics = { seen += it })
        manager.startMetrics { body }
        manager.startMetrics { body }
        runCurrent()
        advanceTimeBy(5001)
        runCurrent()
        // one read at the start of the second loop and one after the interval; the first loop was cancelled before its first read
        assertEquals(2, seen.size)
        manager.stopMetrics()
    }

    private class FakeSource(
        override val usesTls: Boolean = false,
        val info: JSONObject? = JSONObject().put("name", "hydra"),
        val gate: CompletableDeferred<Unit>? = null,
        val settingsAnswer: (Int) -> JSONObject = { JSONObject().put("ok", true) },
        val renewResult: Boolean = true,
    ) : ConnectSource {
        var settingsCalls = 0
        var renewCalls = 0
        override suspend fun hydraInfo(): JSONObject? = info
        override suspend fun settings(): JSONObject {
            gate?.await()
            settingsCalls++
            return settingsAnswer(settingsCalls)
        }
        override suspend fun renewSession(): Boolean {
            renewCalls++
            return renewResult
        }
    }

    private class Recorder : ConnectListener {
        val events = mutableListOf<String>()
        override fun log(message: String) { events += "log:$message" }
        override fun onInfo(info: JSONObject) { events += "info" }
        override fun onReachable(usesTls: Boolean) { events += "reachable:$usesTls" }
        override fun onSettings(settings: JSONObject) { events += "settings" }
        override fun onSynced() { events += "synced" }
        override fun onSyncFailed(error: HydraApiException) { events += "failed:${error.httpCode}" }
        override fun openSocket() { events += "socket" }
    }

    private fun newManager(scope: kotlinx.coroutines.CoroutineScope) =
        ConnectionManager(scope = scope, shouldPoll = { true }, onMetrics = { })

    @Test
    fun `a connect reads the identity then the settings, then lets the socket open`() = runTest {
        val manager = newManager(backgroundScope)
        val recorder = Recorder()
        manager.connect({ FakeSource(usesTls = true) }, recorder)
        assertTrue(manager.isSwitching)
        runCurrent()
        assertEquals(
            listOf("info", "reachable:true", "log:Initial state synchronized via REST", "settings", "synced", "socket"),
            recorder.events,
        )
        assertFalse(manager.isSwitching)
    }

    @Test
    fun `an expired session is renewed once and the settings are read again`() = runTest {
        val manager = newManager(backgroundScope)
        val recorder = Recorder()
        val source = FakeSource(settingsAnswer = { call ->
            if (call == 1) throw HydraApiException("expired", httpCode = 401) else JSONObject()
        })
        manager.connect({ source }, recorder)
        runCurrent()
        assertEquals(1, source.renewCalls)
        assertEquals(2, source.settingsCalls)
        assertTrue(recorder.events.contains("socket"))
    }

    @Test
    fun `a server that cannot be reached ends without a socket and says why`() = runTest {
        val manager = newManager(backgroundScope)
        val recorder = Recorder()
        val source = FakeSource(settingsAnswer = { throw HydraApiException("down", httpCode = 500) })
        manager.connect({ source }, recorder)
        runCurrent()
        assertEquals(0, source.renewCalls)
        assertTrue(recorder.events.contains("failed:500"))
        assertFalse(recorder.events.contains("socket"))
        assertFalse(recorder.events.contains("synced"))
        assertFalse(manager.isSwitching)
    }

    @Test
    fun `a refused renewal ends the connect with the original error`() = runTest {
        val manager = newManager(backgroundScope)
        val recorder = Recorder()
        val source = FakeSource(renewResult = false, settingsAnswer = { throw HydraApiException("expired", httpCode = 401) })
        manager.connect({ source }, recorder)
        runCurrent()
        assertEquals(1, source.renewCalls)
        assertEquals(1, source.settingsCalls)
        assertTrue(recorder.events.contains("failed:401"))
    }

    @Test
    fun `a second connect cancels the first one before it reaches the socket`() = runTest {
        val manager = newManager(backgroundScope)
        val first = Recorder()
        val second = Recorder()
        val gate = CompletableDeferred<Unit>()
        manager.connect({ FakeSource(gate = gate) }, first)
        runCurrent()
        assertTrue(manager.isConnecting)
        manager.connect({ FakeSource() }, second)
        runCurrent()
        gate.complete(Unit)
        runCurrent()
        assertFalse(first.events.contains("socket"))
        assertTrue(second.events.contains("socket"))
    }

    @Test
    fun `cancelling the connect stops the sequence`() = runTest {
        val manager = newManager(backgroundScope)
        val recorder = Recorder()
        val gate = CompletableDeferred<Unit>()
        manager.connect({ FakeSource(gate = gate) }, recorder)
        runCurrent()
        manager.cancelConnect()
        gate.complete(Unit)
        runCurrent()
        assertFalse(manager.isConnecting)
        assertFalse(recorder.events.contains("socket"))
    }
}
