// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - ui/ObscuredTouchPolicy.kt
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.ui

import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import java.util.concurrent.ConcurrentHashMap

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

    // Where the STOP buttons are on screen. A touch on one is never dropped: an overlay that steals a STOP only makes it
    // safe, so the stop must stay reachable even through a covered window.
    private val stopZones = ConcurrentHashMap<Any, RectF>()

    fun setStopZone(key: Any, bounds: RectF) {
        stopZones[key] = bounds
    }

    fun clearStopZone(key: Any) {
        stopZones.remove(key)
    }

    /** True when the touch at ([x], [y]) is dropped: covered window, switch on and the point is not on a STOP button. */
    fun dropsOutsideStop(flags: Int, enabled: Boolean, x: Float, y: Float): Boolean =
        drops(flags, enabled) && stopZones.values.none { it.contains(x, y) }

    /** Marks the composable as a STOP button for [dropsOutsideStop]. */
    fun Modifier.stopTouchZone(): Modifier = composed {
        val key = remember { Any() }
        DisposableEffect(key) { onDispose { clearStopZone(key) } }
        onGloballyPositioned {
            val b = it.boundsInWindow()
            setStopZone(key, RectF(b.left, b.top, b.right, b.bottom))
        }
    }

    /** Sets the system filter of [view] (the decor view of the window) to the state of the switch. */
    fun apply(view: View, enabled: Boolean) {
        view.filterTouchesWhenObscured = enabled
    }

    /** MotionEvent.FLAG_WINDOW_IS_PARTIALLY_OBSCURED (API 29); the value is the same where the flag is never set. */
    private const val FLAG_WINDOW_IS_PARTIALLY_OBSCURED = 0x2
}
