// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - app/src/test/java/com/hydraumc/control/wear/WatchCanOtaPushTest.kt
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.wear

import com.hydraumc.control.model.CanOtaPhase
import com.hydraumc.control.model.CanOtaProgress
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** What the phone passes to the watch about a flash cycle: the message, and how often. */
@RunWith(RobolectricTestRunner::class)
class WatchCanOtaPushTest {
    private fun progress(phase: CanOtaPhase, percent: Int? = null) = CanOtaProgress(phase, percent, null, null, null)

    @Test
    fun `the message carries the phase and the percent in the shape the watch accepts`() {
        val json = WatchCanOtaProgress.of(progress(CanOtaPhase.TRANSFERRING, 40)).toJson()
        assertEquals("canota_progress_status", json.getString("type"))
        assertEquals(1, json.getInt("protocolVersion"))
        assertEquals("transferring", json.getString("phase"))
        assertEquals(40, json.getInt("percent"))
        assertFalse("no percent when there is none", WatchCanOtaProgress.of(progress(CanOtaPhase.DONE)).toJson().has("percent"))
    }

    @Test
    fun `a phase the watch does not know, another version and a percent outside 0 to 100 are refused`() {
        for (build in listOf<() -> Any>(
            { WatchCanOtaProgress("dancing", null) },
            { WatchCanOtaProgress("done", null, protocolVersion = 2) },
            { WatchCanOtaProgress("done", 101) },
            { WatchCanOtaProgress("done", -1) },
        )) {
            try {
                build()
                fail("accepted an invalid progress")
            } catch (_: IllegalArgumentException) {
                // refused
            }
        }
        try {
            WatchCanOtaProgress.fromJson(JSONObject("""{"type":"alert","protocolVersion":1,"phase":"done"}"""))
            fail("accepted another type")
        } catch (_: IllegalArgumentException) {
            // refused
        }
    }

    @Test
    fun `the first report, a change of phase and the end of the cycle are always sent`() {
        val policy = CanOtaWatchPolicy()
        assertTrue(policy.accept(progress(CanOtaPhase.CONNECTING), 0))
        assertTrue(policy.accept(progress(CanOtaPhase.TRANSFERRING, 0), 10))
        assertTrue(policy.accept(progress(CanOtaPhase.VERIFYING, 100), 20))
        assertTrue("the end is sent at once", policy.accept(progress(CanOtaPhase.DONE, 100), 21))
        assertTrue("and so is a second end", policy.accept(progress(CanOtaPhase.DONE, 100), 22))
    }

    @Test
    fun `inside a phase only a step of 5 points after a second is sent`() {
        val policy = CanOtaWatchPolicy()
        assertTrue(policy.accept(progress(CanOtaPhase.TRANSFERRING, 10), 0))
        assertFalse("a step of 2 points", policy.accept(progress(CanOtaPhase.TRANSFERRING, 12), 5_000))
        assertFalse("a step of 5 points too soon", policy.accept(progress(CanOtaPhase.TRANSFERRING, 15), 500))
        assertTrue("a step of 5 points after a second", policy.accept(progress(CanOtaPhase.TRANSFERRING, 15), 1_500))
        assertFalse("no number, nothing to say", policy.accept(progress(CanOtaPhase.TRANSFERRING), 9_000))
        assertTrue("the first number after none is sent", CanOtaWatchPolicy().also { it.accept(progress(CanOtaPhase.TRANSFERRING), 0) }.accept(progress(CanOtaPhase.TRANSFERRING, 1), 2_000))
    }

    @Test
    fun `after a reset the next report is sent whatever it is`() {
        val policy = CanOtaWatchPolicy()
        policy.accept(progress(CanOtaPhase.TRANSFERRING, 50), 0)
        assertFalse(policy.accept(progress(CanOtaPhase.TRANSFERRING, 51), 10))
        policy.reset()
        assertTrue(policy.accept(progress(CanOtaPhase.TRANSFERRING, 51), 20))
    }
}
