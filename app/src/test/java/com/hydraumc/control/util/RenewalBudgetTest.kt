// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - util/RenewalBudgetTest.kt
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RenewalBudgetTest {
    private var now = 1_000_000L
    private fun budget() = RenewalBudget(minGapMs = 20_000, windowMs = 600_000, maxPerWindow = 3, clockMs = { now })

    @Test fun `the first renewal is allowed`() {
        assertTrue(budget().tryAcquire())
    }

    @Test fun `a second renewal right after the first is refused`() {
        val b = budget()
        assertTrue(b.tryAcquire())
        now += 5_000
        assertFalse(b.tryAcquire())
    }

    @Test fun `renewals spaced out are allowed up to the budget of the window and then refused`() {
        val b = budget()
        assertTrue(b.tryAcquire()); now += 30_000
        assertTrue(b.tryAcquire()); now += 30_000
        assertTrue(b.tryAcquire()); now += 30_000
        assertFalse(b.tryAcquire())
    }

    @Test fun `the budget comes back once the window has passed`() {
        val b = budget()
        repeat(3) { assertTrue(b.tryAcquire()); now += 30_000 }
        assertFalse(b.tryAcquire())
        now += 601_000
        assertTrue(b.tryAcquire())
    }

    @Test fun `a sign-in typed by hand starts the count again`() {
        val b = budget()
        assertTrue(b.tryAcquire())
        b.reset()
        assertTrue(b.tryAcquire())
    }
}
