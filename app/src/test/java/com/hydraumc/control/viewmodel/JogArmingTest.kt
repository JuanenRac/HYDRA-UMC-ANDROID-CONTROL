// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - app/src/test/java/com/hydraumc/control/viewmodel/JogArmingTest.kt
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.viewmodel

import com.hydraumc.control.network.HydraApiException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The arming of the jog as the phone shows it, and what the work-file screens do with a failed read. */
class JogArmingTest {
    @Test
    fun anArmingInTheFutureIsKeptButCappedByItsLength() {
        val armed = JogArming.fromServer(emptyMap(), 1, untilServerMs = 1_000_000L, nowMs = 1_000L, ttlMs = 60_000L)
        assertEquals(61_000L, armed[1])
    }

    @Test
    fun anArmingShorterThanItsLengthKeepsTheServerMoment() {
        val armed = JogArming.fromServer(emptyMap(), 1, untilServerMs = 5_000L, nowMs = 1_000L, ttlMs = 60_000L)
        assertEquals(5_000L, armed[1])
    }

    @Test
    fun aPastOrMissingArmingRemovesTheEntry() {
        val before = mapOf(1 to 9_000L, 2 to 9_000L)
        assertEquals(mapOf(2 to 9_000L), JogArming.fromServer(before, 1, 500L, 1_000L, 60_000L))
        assertEquals(mapOf(2 to 9_000L), JogArming.fromServer(before, 1, null, 1_000L, 60_000L))
    }

    @Test
    fun armingAndDisarmingFromThisPhone() {
        val armed = JogArming.afterRequest(emptyMap(), 3, arm = true, nowMs = 100L, ttlMs = 50L)
        assertEquals(150L, armed[3])
        assertTrue(JogArming.afterRequest(armed, 3, arm = false, nowMs = 100L, ttlMs = 50L).isEmpty())
    }

    @Test
    fun theJogNeedsArmingWhenTheEntryIsMissingOrOver() {
        val armed = mapOf(1 to 2_000L)
        assertFalse(JogArming.needsArming(armed, 1, 1_999L))
        assertTrue(JogArming.needsArming(armed, 1, 2_000L))
        assertTrue(JogArming.needsArming(armed, 2, 0L))
    }

    private fun error(http: Int?) = HydraApiException("x", httpCode = http)

    @Test
    fun aMissingFolderIsAnEmptyList() {
        assertEquals(WorkFileFlow.ListOutcome.EMPTY_LIST, WorkFileFlow.listOutcome(error(404)))
        assertEquals(WorkFileFlow.ListOutcome.ERROR, WorkFileFlow.listOutcome(error(500)))
        assertEquals(WorkFileFlow.ListOutcome.ERROR, WorkFileFlow.listOutcome(RuntimeException("boom")))
    }

    @Test
    fun onlyAnEndedSessionIsRenewed() {
        assertTrue(WorkFileFlow.sessionEnded(error(401)))
        assertFalse(WorkFileFlow.sessionEnded(error(404)))
        assertFalse(WorkFileFlow.sessionEnded(IllegalStateException()))
    }
}
