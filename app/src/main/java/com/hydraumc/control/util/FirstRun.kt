// =============================================================================
// HYDRA-UMC CONTROL - Whether this phone has been used before
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.util

import android.content.Context

/** One flag: the welcome has been left behind. It holds nothing about the person or the server. */
object FirstRun {
    private const val FILE = "hydra_first_run"
    private const val SEEN = "seen"

    /** True on a phone that never signed in: no flag, and no user name kept from before this flag existed. */
    fun isFirst(context: Context, savedUsername: String): Boolean =
        savedUsername.isBlank() && !context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(SEEN, false)

    fun markSeen(context: Context) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean(SEEN, true).apply()
    }
}
