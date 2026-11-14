// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - app/src/test/java/com/hydraumc/control/model/CanOtaProgressTest.kt
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.model

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The reading of the `canota_progress` message: what the server sends is shown, and nothing that is not there is invented. */
@RunWith(RobolectricTestRunner::class)
class CanOtaProgressTest {

    private fun message(payload: String) = JSONObject("""{"type":"canota_progress","payload":$payload}""")

    @Test
    fun `a transfer message gives the phase, the pages and the percent`() {
        val progress = CanOtaProgress.fromMessage(
            message("""{"phase":"transferring","pages_sent":10,"pages_total":40,"percent":25,"error":null}"""),
        )!!
        assertEquals(CanOtaPhase.TRANSFERRING, progress.phase)
        assertEquals(10, progress.pagesSent)
        assertEquals(40, progress.pagesTotal)
        assertEquals(25, progress.percent)
        assertNull(progress.error)
        assertFalse(progress.finished)
    }

    @Test
    fun `every phase the server writes is known and done and error end the cycle`() {
        val names = listOf("connecting", "entering_bootloader", "erasing_fram", "transferring", "verifying", "rebooting", "done", "error")
        assertEquals(names, CanOtaPhase.entries.map { it.wire })
        for (name in names) {
            val progress = CanOtaProgress.fromMessage(message("""{"phase":"$name"}"""))!!
            assertEquals(name in listOf("done", "error"), progress.finished)
        }
    }

    @Test
    fun `an error carries its reason and a long reason is cut`() {
        val reason = "x".repeat(500)
        val progress = CanOtaProgress.fromMessage(message("""{"phase":"error","error":"$reason"}"""))!!
        assertTrue(progress.finished)
        assertEquals(CanOtaProgress.MAX_REASON_CHARS, progress.error!!.length)
        assertNull(CanOtaProgress.fromMessage(message("""{"phase":"error","error":"  "}"""))!!.error)
    }

    @Test
    fun `a missing or wrong number stays null and a percent is kept inside 0 to 100`() {
        val none = CanOtaProgress.fromMessage(message("""{"phase":"verifying"}"""))!!
        assertNull(none.percent)
        assertNull(none.pagesSent)
        assertNull(none.pagesTotal)
        val wrong = CanOtaProgress.fromMessage(message("""{"phase":"transferring","pages_sent":-1,"pages_total":2.5,"percent":"half"}"""))!!
        assertNull(wrong.percent)
        assertNull(wrong.pagesSent)
        assertNull(wrong.pagesTotal)
        assertEquals(100, CanOtaProgress.fromMessage(message("""{"phase":"done","percent":140}"""))!!.percent)
        assertEquals(0, CanOtaProgress.fromMessage(message("""{"phase":"connecting","percent":-5}"""))!!.percent)
        assertEquals(34, CanOtaProgress.fromMessage(message("""{"phase":"transferring","percent":33.6}"""))!!.percent)
    }

    @Test
    fun `a message with no payload or an unknown phase is not a progress`() {
        assertNull(CanOtaProgress.fromMessage(JSONObject("""{"type":"canota_progress"}""")))
        assertNull(CanOtaProgress.fromMessage(message("""{"phase":"dancing"}""")))
        assertNull(CanOtaProgress.fromMessage(message("""{"percent":10}""")))
    }
}
