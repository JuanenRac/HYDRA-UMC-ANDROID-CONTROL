// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - app/src/test/java/com/hydraumc/control/util/LinkHealthTest.kt
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The level of the link follows the slowest recent answer; a silent link is lost; only a bad or lost link blocks the jog. */
class LinkHealthTest {
    private var now = 1_000L
    private fun health() = LinkHealth(clock = { now })

    @Test
    fun nothingMeasuredIsUnknownAndDoesNotBlock() {
        val h = health()
        assertEquals(LinkHealth.Level.UNKNOWN, h.level())
        assertFalse(h.blocksJog())
        assertNull(h.worstMs())
    }

    @Test
    fun theLevelsFollowTheContractThresholds() {
        val h = health()
        h.record(40)
        assertEquals(LinkHealth.Level.GOOD, h.level())
        h.reset()
        h.record(150)
        assertEquals(LinkHealth.Level.POOR, h.level())
        h.reset()
        h.record(300)
        assertEquals(LinkHealth.Level.BAD, h.level())
        assertFalse(h.blocksJog())
        h.reset()
        h.record(500)
        assertTrue(h.blocksJog())
    }

    @Test
    fun theSlowestRecentSampleDecides() {
        val h = health()
        repeat(7) { h.record(20) }
        h.record(520)
        assertEquals(520L, h.worstMs())
        assertTrue(h.blocksJog())
    }

    @Test
    fun aSlowSampleLeavesTheWindowAfterEightFastOnes() {
        val h = health()
        h.record(520)
        repeat(8) { h.record(20) }
        assertEquals(LinkHealth.Level.GOOD, h.level())
        assertFalse(h.blocksJog())
    }

    @Test
    fun silenceMakesTheLinkLostAndBlocks() {
        val h = health()
        h.record(20)
        now += 4_001
        assertEquals(LinkHealth.Level.LOST, h.level())
        assertTrue(h.blocksJog())
    }

    @Test
    fun twoFailedProbesInARowMakeItLostAnAnswerRecoversIt() {
        val h = health()
        h.record(20)
        h.recordFailure()
        assertEquals(LinkHealth.Level.GOOD, h.level())
        h.recordFailure()
        assertEquals(LinkHealth.Level.LOST, h.level())
        h.record(30)
        assertEquals(LinkHealth.Level.GOOD, h.level())
    }

    @Test
    fun limitsOutOfOrderAreRefused() {
        assertTrue(runCatching { LinkHealth.Limits(goodRttMs = 300, poorRttMs = 100) }.isFailure)
    }
}
