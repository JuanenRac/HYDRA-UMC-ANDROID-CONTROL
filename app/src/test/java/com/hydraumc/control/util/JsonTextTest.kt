// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - JSON null text handling tests
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.util

import com.hydraumc.control.model.RobotView
import com.hydraumc.control.update.ReleaseMetadataParser
import com.hydraumc.control.update.UpdateCheckResult
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** `JSONObject.optString` answers the text "null" for a JSON null; [optStr] answers the fallback. */
@RunWith(RobolectricTestRunner::class)
class JsonTextTest {
    @Test fun `a JSON null is the fallback and not the word null`() {
        val json = JSONObject("""{"name":null,"role":"Picker"}""")
        // the platform behaviour this helper exists to avoid
        assertEquals("null", json.optString("name", "fallback"))
        assertEquals("fallback", json.optStr("name", "fallback"))
        assertEquals("", json.optStr("name"))
    }

    @Test fun `a missing key and a real value behave as before`() {
        val json = JSONObject("""{"role":"Picker"}""")
        assertEquals("fallback", json.optStr("absent", "fallback"))
        assertEquals("", json.optStr("absent"))
        assertEquals("Picker", json.optStr("role", "fallback"))
    }

    @Test fun `a robot with null fields shows its defaults`() {
        val robot = RobotView(JSONObject("""{"id":3,"name":null,"model":null,"role":null,"tool":null}"""))
        assertEquals("Robot 3", robot.name)
        assertEquals("Generic (6-DOF)", robot.model)
        assertEquals("Idle", robot.role)
        assertEquals("None", robot.tool)
    }

    @Test fun `release notes that are JSON null are empty and not the word null`() {
        val payload = """
            {"tag_name":"v9.9.9","name":null,"body":null,"assets":[
              {"name":"${ReleaseMetadataParser.REQUIRED_ASSET_NAME}","browser_download_url":"https://example.invalid/app.apk"}
            ]}
        """.trimIndent()
        val result = ReleaseMetadataParser.parseLatestStable(payload, "0.0.1")
        assertTrue(result is UpdateCheckResult.Available)
        val update = (result as UpdateCheckResult.Available).update
        assertEquals("", update.notes)
        assertEquals("HYDRA-UMC CONTROL v9.9.9", update.releaseName)
    }
}
