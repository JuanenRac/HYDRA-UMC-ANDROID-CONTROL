// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - app/src/test/java/com/hydraumc/control/util/OptimisticRollbackTest.kt
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.util

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.runner.RunWith
import org.junit.Test
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OptimisticRollbackTest {

    private fun robot() = JSONObject("""{"id":1,"pos":{"x":0,"y":0,"z":0},"playbackState":{"isPlaying":false}}""")

    @Test
    fun `diff lists only the leaves the edit changed`() {
        val before = robot()
        val after = robot()
        after.getJSONObject("pos").put("x", 5)
        val changes = OptimisticRollback.diff(before, after)
        assertEquals(1, changes.size)
        assertEquals(listOf("pos", "x"), changes[0].path)
    }

    @Test
    fun `a failed edit is undone`() {
        val before = robot()
        val live = robot()
        live.getJSONObject("pos").put("x", 5)
        val changes = OptimisticRollback.diff(before, live)
        assertEquals(1, OptimisticRollback.restore(live, changes))
        assertEquals(0, live.getJSONObject("pos").getInt("x"))
    }

    @Test
    fun `newer values written after the edit survive the rollback`() {
        val before = robot()
        val live = robot()
        live.getJSONObject("pos").put("x", 5)
        val changes = OptimisticRollback.diff(before, live)
        // A later jog succeeded on y, and a server delta moved x again.
        live.getJSONObject("pos").put("y", 9)
        live.getJSONObject("pos").put("x", 7)
        assertEquals(0, OptimisticRollback.restore(live, changes))
        assertEquals(9, live.getJSONObject("pos").getInt("y"))
        assertEquals(7, live.getJSONObject("pos").getInt("x"))
    }

    @Test
    fun `a field the edit added is removed again`() {
        val before = robot()
        val live = robot()
        live.put("extra", true)
        val changes = OptimisticRollback.diff(before, live)
        OptimisticRollback.restore(live, changes)
        assertFalse(live.has("extra"))
        assertTrue(live.has("pos"))
    }
}
