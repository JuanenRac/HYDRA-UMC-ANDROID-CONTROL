// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - Address of the embedded 3D viewer
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.util

import java.net.URLEncoder

/**
 * Builds the address the embedded 3D viewer loads. The access token goes in the fragment
 * (`#token=...`): a fragment is never sent to the server, so no access log, proxy or referrer
 * ever sees it. The viewer reads it from there and drops it from the address.
 */
object ViewerUrl {
    /** Address of the viewer for [robotId]; [token] may be empty (the viewer then asks for a login). */
    fun build(scheme: String, host: String, port: String, robotId: String, token: String): String {
        val base = "$scheme://$host:$port/?hideUI=true&robotId=${encode(robotId)}"
        return if (token.isEmpty()) base else "$base#token=${encode(token)}"
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")
}
