// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - app/src/test/java/com/hydraumc/control/util/RenewalBudgetEdgeTest.kt
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger

/**
 * How often the app may renew the session by itself: at least 20 s between two
 * renewals and no more than 3 in 10 minutes, then back to the sign-in, so a
 * session the server keeps rejecting cannot turn into a login loop.
 */
class RenewalBudgetEdgeTest {

    private var now = 1_000_000L
    private fun budget(minGapMs: Long = 20_000L, windowMs: Long = 600_000L, max: Int = 3) =
        RenewalBudget(minGapMs, windowMs, max) { now }

    @Test
    fun `the first renewal is always allowed`() {
        assertTrue(budget().tryAcquire())
    }

    @Test
    fun `a second renewal right after the first is refused`() {
        val b = budget()
        assertTrue(b.tryAcquire())
        assertFalse(b.tryAcquire())
        now += 19_999
        assertFalse(b.tryAcquire())
    }

    @Test
    fun `a renewal is allowed again once the minimum gap has passed`() {
        val b = budget()
        assertTrue(b.tryAcquire())
        now += 20_000
        assertTrue(b.tryAcquire())
    }

    @Test
    fun `a refused attempt is not counted and does not push the gap forward`() {
        val b = budget()
        assertTrue(b.tryAcquire())
        now += 10_000
        assertFalse(b.tryAcquire())
        now += 10_000 // 20 s after the renewal that counted, not 20 s after the refused attempt
        assertTrue(b.tryAcquire())
    }

    @Test
    fun `no more than the allowed number fit in the window`() {
        val b = budget()
        repeat(3) {
            assertTrue("renewal ${it + 1}", b.tryAcquire())
            now += 25_000
        }
        assertFalse(b.tryAcquire())
        now += 25_000
        assertFalse("still inside the window of the first renewal", b.tryAcquire())
    }

    @Test
    fun `renewals leave the window and make room again`() {
        val b = budget()
        repeat(3) {
            assertTrue(b.tryAcquire())
            now += 25_000
        }
        assertFalse(b.tryAcquire())
        now += 600_000 - 3 * 25_000 + 1 // the first renewal is now older than the window
        assertTrue(b.tryAcquire())
    }

    @Test
    fun `the window edge is exclusive on its far side`() {
        val b = budget(minGapMs = 1, max = 1)
        assertTrue(b.tryAcquire())
        now += 600_000 // exactly one window later the renewal still counts
        assertFalse(b.tryAcquire())
        now += 1
        assertTrue(b.tryAcquire())
    }

    @Test
    fun `a sign-in typed by the person starts the count again`() {
        val b = budget()
        assertTrue(b.tryAcquire())
        assertFalse(b.tryAcquire())
        b.reset()
        assertTrue(b.tryAcquire())
    }

    @Test
    fun `the default budget is 20 seconds apart and three in ten minutes`() {
        var t = 0L
        val b = RenewalBudget(clockMs = { t })
        assertTrue(b.tryAcquire())
        t += 19_999
        assertFalse(b.tryAcquire())
        t += 1
        assertTrue(b.tryAcquire())
        t += 20_000
        assertTrue(b.tryAcquire())
        t += 20_000
        assertFalse(b.tryAcquire())
    }

    @Test
    fun `many threads asking at once get exactly one renewal`() {
        val b = RenewalBudget(clockMs = { 5L })
        val granted = AtomicInteger()
        val start = CountDownLatch(1)
        val threads = List(16) {
            Thread {
                start.await()
                if (b.tryAcquire()) granted.incrementAndGet()
            }.also { it.start() }
        }
        start.countDown()
        threads.forEach { it.join() }
        assertEquals(1, granted.get())
    }
}

