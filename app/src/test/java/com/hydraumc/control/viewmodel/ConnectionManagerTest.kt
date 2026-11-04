// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - test/viewmodel/ConnectionManagerTest.kt
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.viewmodel

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

/** The metrics poll: it reads while it should, skips a failed read, stops when it should not run and restarts without doubling. */
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
}
