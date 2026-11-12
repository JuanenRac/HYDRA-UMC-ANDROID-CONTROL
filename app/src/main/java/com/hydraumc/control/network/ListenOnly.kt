// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - network/ListenOnly.kt
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.network

/**
 * Listen only: the app shows the robots and their data but sends no robot command. The only ones that still go out are
 * STOP and the safe-mode disable, because refusing a stop would be the unsafe side. Off by default.
 */
object ListenOnly {
    private val ALWAYS_ALLOWED: Set<String> = setOf(RobotCommandContract.STOP, RobotCommandContract.DISABLE)

    /** Whether [command] may leave the app while Listen only is [on]. */
    fun allows(on: Boolean, command: String): Boolean = !on || command in ALWAYS_ALLOWED
}
