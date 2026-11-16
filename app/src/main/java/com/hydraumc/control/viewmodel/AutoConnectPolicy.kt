// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - Which discovered server the app connects to by itself
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.viewmodel

/**
 * Connecting sends the stored session (and the remembered password) to the server, so the app connects by itself only to the server it saved
 * before. A server found on the network that is not that one, and every server on the first start (nothing saved), waits for a tap.
 */
object AutoConnectPolicy {
    /** True when a discovered server at [host]:[port] is the [saved] one (host and port). */
    fun allows(saved: Pair<String, Int>?, host: String, port: Int): Boolean =
        saved != null && saved.first.isNotBlank() && saved.first.equals(host, ignoreCase = true) && saved.second == port
}
