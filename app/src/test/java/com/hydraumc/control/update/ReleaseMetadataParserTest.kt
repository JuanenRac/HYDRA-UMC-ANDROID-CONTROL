// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - GitHub Release metadata parser unit tests
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0 - see LICENSE
// =============================================================================
package com.hydraumc.control.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ReleaseMetadataParserTest {
    private fun release(tag: String, url: String = "https://example.invalid/app.apk", extras: String = "") = """
        {"tag_name":"$tag","name":"Release $tag","body":" notes ","assets":[
          {"name":"${ReleaseMetadataParser.REQUIRED_ASSET_NAME}","browser_download_url":"$url"}
        ]$extras}
    """.trimIndent()

    @Test fun `accepts only a newer stable release with the exact HTTPS APK asset`() {
        val result = ReleaseMetadataParser.parseLatestStable(release("v0.4.5"), "0.4.4")
        assertTrue(result is UpdateCheckResult.Available)
        assertEquals(SemanticVersion(0, 4, 5), (result as UpdateCheckResult.Available).update.version)
    }

    @Test fun `rejects non stable release tag missing asset and non HTTPS URL`() {
        assertTrue(ReleaseMetadataParser.parseLatestStable(release("v0.4.5-beta"), "0.4.4") is UpdateCheckResult.Failed)
        assertTrue(ReleaseMetadataParser.parseLatestStable("{\"tag_name\":\"v0.4.5\",\"assets\":[]}", "0.4.4") is UpdateCheckResult.Failed)
        assertTrue(ReleaseMetadataParser.parseLatestStable(release("v0.4.5", "http://example.invalid/app.apk"), "0.4.4") is UpdateCheckResult.Failed)
    }

    @Test fun `does not offer draft prerelease or non newer releases`() {
        assertEquals(UpdateCheckResult.UpToDate, ReleaseMetadataParser.parseLatestStable(release("v0.4.5", extras = ",\"draft\":true"), "0.4.4"))
        assertEquals(UpdateCheckResult.UpToDate, ReleaseMetadataParser.parseLatestStable(release("v0.4.4"), "0.4.4"))
    }
}
