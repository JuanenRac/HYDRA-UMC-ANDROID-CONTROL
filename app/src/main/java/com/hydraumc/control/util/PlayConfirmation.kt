// =============================================================================
// HYDRA-UMC CONTROL - When PLAY asks first
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.util

import kotlin.math.roundToInt

/**
 * The rules of the confirmation before PLAY starts a trajectory: it asks when the person left the option on
 * (it is on until they turn it off) and the robot is not already playing (a robot that is playing or paused
 * is resumed or restarted from the console it is already on, with no new trajectory to confirm).
 */
object PlayConfirmation {
    fun needed(askBeforePlay: Boolean, robotIsPlaying: Boolean): Boolean = askBeforePlay && !robotIsPlaying

    /** The speed of the robot as a whole percent for the summary; a value that is not a number or is negative is 0. */
    fun speedPercent(speed: Double): Int = if (speed.isNaN() || speed.isInfinite() || speed < 0.0) 0 else speed.roundToInt()
}
