// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - What this app asks of the release feed
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.update

import com.hydraumc.common.ReleaseMetadataParser
import com.hydraumc.common.UpdateCheckResult

/** The trust gate lives in the shared module; this app only names its own asset. */
object ControlRelease {
    const val REQUIRED_ASSET_NAME = "HYDRA-UMC-ANDROID-CONTROL-release.apk"

    fun parseLatestStable(payload: String, installedVersionName: String): UpdateCheckResult =
        ReleaseMetadataParser.parseLatestStable(payload, installedVersionName, REQUIRED_ASSET_NAME, "HYDRA-UMC CONTROL")
}
