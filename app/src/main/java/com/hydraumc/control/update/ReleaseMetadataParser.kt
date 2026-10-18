// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - Safe GitHub Release metadata parser
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.update

import com.hydraumc.control.util.optStr
import org.json.JSONObject

/**
 * Pure release-metadata gate used before any APK is downloaded. Keeping it
 * free of Context, HTTP and package-manager APIs makes the update channel's
 * trust decisions directly testable on the JVM.
 */
object ReleaseMetadataParser {
    const val REQUIRED_ASSET_NAME = "HYDRA-UMC-ANDROID-CONTROL-release.apk"

    fun parseLatestStable(payload: String, installedVersionName: String): UpdateCheckResult {
        val release = JSONObject(payload)
        if (release.optBoolean("draft") || release.optBoolean("prerelease")) {
            return UpdateCheckResult.UpToDate
        }
        val remoteVersion = SemanticVersion.parseStable(release.optStr("tag_name"))
            ?: return UpdateCheckResult.Failed("Latest GitHub Release tag is not a stable vMAJOR.MINOR.PATCH value.")
        val localVersion = SemanticVersion.parseStable(installedVersionName)
            ?: return UpdateCheckResult.Failed("Installed application version is not stable semver.")
        if (remoteVersion <= localVersion) return UpdateCheckResult.UpToDate

        val assets = release.optJSONArray("assets")
            ?: return UpdateCheckResult.Failed("Release v$remoteVersion has no $REQUIRED_ASSET_NAME asset.")
        val asset = (0 until assets.length())
            .asSequence()
            .map { assets.optJSONObject(it) }
            .firstOrNull { it?.optStr("name") == REQUIRED_ASSET_NAME }
            ?: return UpdateCheckResult.Failed("Release v$remoteVersion has no $REQUIRED_ASSET_NAME asset.")
        val assetUrl = asset.optStr("browser_download_url")
        if (!assetUrl.startsWith("https://")) {
            return UpdateCheckResult.Failed("Release APK URL is not HTTPS.")
        }
        return UpdateCheckResult.Available(
            AvailableUpdate(
                version = remoteVersion,
                releaseName = release.optStr("name", "HYDRA-UMC CONTROL v$remoteVersion"),
                notes = release.optStr("body").trim(),
                assetUrl = assetUrl,
            ),
        )
    }
}
