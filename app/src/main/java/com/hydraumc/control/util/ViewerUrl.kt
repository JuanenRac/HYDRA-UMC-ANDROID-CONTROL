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

    /** True when the viewer must refuse plain-HTTP parts inside an HTTPS page (a session that is HTTPS keeps all of it encrypted). */
    fun blocksMixedContent(scheme: String): Boolean = scheme.equals("https", ignoreCase = true)

    /** True when [target] is an address on the server the viewer was opened from; anything else is not followed inside the viewer. */
    fun staysOnServer(target: String?, host: String): Boolean {
        val parsed = runCatching { java.net.URI(target ?: return false) }.getOrNull() ?: return false
        return parsed.host.equals(host, ignoreCase = true)
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")
}
