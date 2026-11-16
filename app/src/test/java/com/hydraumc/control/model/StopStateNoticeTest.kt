// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - app/src/test/java/com/hydraumc/control/model/StopStateNoticeTest.kt
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.model

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The reading of the `stop-state` message: only a stop restored after a restart raises the notice. */
@RunWith(RobolectricTestRunner::class)
class StopStateNoticeTest {
    private fun message(software: String, restored: String = "") =
        JSONObject("""{"type":"stop-state","schema":2,"state":{"state":"STOPPED","physical":"unknown","software":"$software"$restored}}""")

    @Test
    fun `a stop restored after a restart raises the notice`() {
        assertEquals(true, StopStateNotice.restoredAfterRestart(message("stopped", ",\"restoredAfterRestart\":true")))
    }

    @Test
    fun `a stop pressed in this run or a released one does not`() {
        assertEquals(false, StopStateNotice.restoredAfterRestart(message("stopped")))
        assertEquals(false, StopStateNotice.restoredAfterRestart(message("ready", ",\"restoredAfterRestart\":true")))
    }

    @Test
    fun `anything that is not a complete stop-state message leaves the notice as it was`() {
        assertNull(StopStateNotice.restoredAfterRestart(JSONObject("""{"type":"claims"}""")))
        assertNull(StopStateNotice.restoredAfterRestart(JSONObject("""{"type":"stop-state"}""")))
        assertNull(StopStateNotice.restoredAfterRestart(JSONObject("""{"type":"stop-state","state":{"software":"odd"}}""")))
    }
}
