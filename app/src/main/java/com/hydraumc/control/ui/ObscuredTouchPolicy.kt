// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - ui/ObscuredTouchPolicy.kt
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.ui

import android.view.MotionEvent
import android.view.View

/**
 * Protection against a touch given to a button that another app hides (clickjacking). While the switch is on, the system drops
 * every touch that reaches the window when another window draws over it, so the STOP and the other controls cannot be pressed
 * through an overlay. The switch is on by default; it can be turned off in Settings for an overlay that is harmless (a screen
 * filter) and blocks the buttons.
 */
object ObscuredTouchPolicy {
    const val ENABLED_BY_DEFAULT = true

    /** True when a touch with these MotionEvent [flags] is dropped: the window is covered (wholly or in part) and the switch is on. */
    fun drops(flags: Int, enabled: Boolean): Boolean =
        enabled && (flags and (MotionEvent.FLAG_WINDOW_IS_OBSCURED or FLAG_WINDOW_IS_PARTIALLY_OBSCURED)) != 0

    /** Sets the system filter of [view] (the decor view of the window) to the state of the switch. */
    fun apply(view: View, enabled: Boolean) {
        view.filterTouchesWhenObscured = enabled
    }

    /** MotionEvent.FLAG_WINDOW_IS_PARTIALLY_OBSCURED (API 29); the value is the same where the flag is never set. */
    private const val FLAG_WINDOW_IS_PARTIALLY_OBSCURED = 0x2
}
