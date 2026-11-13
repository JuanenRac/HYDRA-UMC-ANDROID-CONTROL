// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - app/src/test/java/com/hydraumc/control/ui/MjpegReconnectPolicyTest.kt
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MjpegReconnectPolicyTest {
    @Test
    fun theFirstRetryWaitsOneSecond() {
        assertEquals(1_000L, MjpegReconnectPolicy.delayMs(0))
    }

    @Test
    fun theWaitDoublesAndStopsAtTheCeiling() {
        val waits = (0..8).map { MjpegReconnectPolicy.delayMs(it) }
        assertEquals(listOf(1_000L, 2_000L, 4_000L, 8_000L, 15_000L, 15_000L, 15_000L, 15_000L, 15_000L), waits)
    }

    @Test
    fun theScheduleNeverShrinksWhileNothingArrives() {
        var failures = 0
        var last = 0L
        repeat(40) {
            failures = MjpegReconnectPolicy.failuresAfter(failures, gotFrame = false)
            val wait = MjpegReconnectPolicy.delayMs(failures)
            assertTrue(wait >= last)
            assertTrue(wait <= MjpegReconnectPolicy.MAX_DELAY_MS)
            last = wait
        }
    }

    @Test
    fun aFrameResetsTheScheduleToTheFirstWait() {
        var failures = 0
        repeat(6) { failures = MjpegReconnectPolicy.failuresAfter(failures, gotFrame = false) }
        assertEquals(15_000L, MjpegReconnectPolicy.delayMs(failures))
        failures = MjpegReconnectPolicy.failuresAfter(failures, gotFrame = true)
        assertEquals(0, failures)
        assertEquals(1_000L, MjpegReconnectPolicy.delayMs(failures))
    }

    @Test
    fun aNegativeCountIsTreatedAsNoFailure() {
        assertEquals(1_000L, MjpegReconnectPolicy.delayMs(-3))
        assertEquals(1, MjpegReconnectPolicy.failuresAfter(-5, gotFrame = false))
    }

    @Test
    fun aHugeCountDoesNotOverflow() {
        assertEquals(15_000L, MjpegReconnectPolicy.delayMs(Int.MAX_VALUE))
        assertEquals(1_001, MjpegReconnectPolicy.failuresAfter(Int.MAX_VALUE, gotFrame = false))
    }
}
