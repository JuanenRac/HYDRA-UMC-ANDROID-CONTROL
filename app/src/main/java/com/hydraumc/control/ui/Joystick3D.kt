// =============================================================================
// HYDRA-UMC CONTROL - UI Control Component: Joystick3D.kt
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0 - see LICENSE
// =============================================================================
// A jog-pendant-style directional pad for X/Y/Z - a straight Compose port of
// HYDRA-UMC-STUDIO's own src/components/Joystick3D.tsx (same name, same
// layout, same REPEAT_MS, same onJog(dx,dy,dz) signed-multiplier contract),
// not a redesign: the owner asked for the SAME joystick STUDIO already uses
// for robot A1, not a new one. Deliberately a D-pad with real diagonal
// buttons, not an analog stick - matches a real CNC/robot jog pendant, and
// matches jogStep already working elsewhere in this app (ControlScreen's
// own step-size chips).
package com.hydraumc.control.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.East
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.North
import androidx.compose.material.icons.filled.NorthEast
import androidx.compose.material.icons.automirrored.filled.RotateLeft
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.NorthWest
import androidx.compose.material.icons.filled.South
import androidx.compose.material.icons.filled.SouthEast
import androidx.compose.material.icons.filled.SouthWest
import androidx.compose.material.icons.filled.West
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val REPEAT_MS = 150L

private val padBtnSize = 40.dp

/**
 * XYZ jog D-pad - press-and-hold repeat (fires immediately on press, then
 * every REPEAT_MS while held), diagonals send both X and Y in one call
 * exactly like STUDIO's own onJog(dx,dy,dz).
 *
 * @param onJog Called with the signed step multiplier for each axis pressed
 *   - e.g. (1,0,0) for +X, (1,1,0) for a diagonal +X+Y press. Caller applies
 *   its own step size/units, same contract as STUDIO's Joystick3D.tsx.
 * @param zEnabled Separately gates just the Z column - unlike STUDIO (which
 *   only ever jogs the robot arm), this app's Control screen also jogs an
 *   XY table with no Z axis of its own (see ControlScreen.kt's own
 *   activeTarget toggle) - defaults to `enabled` so a caller that doesn't
 *   need the distinction can ignore this entirely.
 */
@Composable
fun Joystick3D(
    onJog: (dx: Int, dy: Int, dz: Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    zEnabled: Boolean = enabled,
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        JoystickXYPad(onJog = onJog, enabled = enabled)
        JoystickZColumn(onJog = onJog, enabled = zEnabled)
    }
}

/**
 * Just the XY pad half of [Joystick3D] - split out so a fullscreen
 * landscape layout (ThreeDScreen.kt's own fullscreen mode) can place it
 * and [JoystickZColumn] on opposite sides of the screen like a game
 * controller's two thumbsticks, instead of only ever together as one
 * fixed unit. [Joystick3D] itself is unchanged for every existing caller
 * (ControlScreen.kt) - this is purely an extraction, not a new control.
 */
@Composable
fun JoystickXYPad(
    onJog: (dx: Int, dy: Int, dz: Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    @Composable
    fun JogButton(
        dx: Int,
        dy: Int,
        dz: Int,
        icon: ImageVector,
        active: Boolean,
        tint: Color = Color(0xFF38BDF8),
        size: androidx.compose.ui.unit.Dp = padBtnSize,
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .background(Color(0xFF0F172A).copy(alpha = if (active) 0.8f else 0.4f), RoundedCornerShape(8.dp))
                .border(1.dp, Color(0xFF334155).copy(alpha = if (active) 1f else 0.4f), RoundedCornerShape(8.dp))
                .pointerInput(dx, dy, dz, active) {
                    if (!active) return@pointerInput
                    // Same press-and-hold-repeat contract as STUDIO's own
                    // onPointerDown (fire immediately) + setInterval(REPEAT_MS)
                    // + onPointerUp/onPointerLeave (stop) - ported to Compose's
                    // gesture primitives instead of DOM pointer events.
                    coroutineScope {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            val job = launch {
                                while (isActive) {
                                    onJog(dx, dy, dz)
                                    delay(REPEAT_MS)
                                }
                            }
                            waitForUpOrCancellation()
                            job.cancel()
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = if (active) tint else tint.copy(alpha = 0.4f), modifier = Modifier.size(18.dp))
        }
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        // 3x3 grid, diagonals included (matches a typical jog pendant
        // layout) plus a non-interactive center crosshair marker.
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            JogButton(-1, 1, 0, Icons.Filled.NorthWest, active = enabled)
            JogButton(0, 1, 0, Icons.Filled.North, active = enabled)
            JogButton(1, 1, 0, Icons.Filled.NorthEast, active = enabled)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            JogButton(-1, 0, 0, Icons.Filled.West, active = enabled)
            Box(
                modifier = Modifier.size(padBtnSize),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.GpsFixed, contentDescription = null, tint = Color(0xFF334155), modifier = Modifier.size(16.dp))
            }
            JogButton(1, 0, 0, Icons.Filled.East, active = enabled)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            JogButton(-1, -1, 0, Icons.Filled.SouthWest, active = enabled)
            JogButton(0, -1, 0, Icons.Filled.South, active = enabled)
            JogButton(1, -1, 0, Icons.Filled.SouthEast, active = enabled)
        }
    }
}

/**
 * 2 dedicated base-rotation (J1) buttons - requested directly, alongside
 * the existing XYZ jog D-pad, so rotating the base and jogging the tool
 * point are both reachable from the one control an operator already has
 * open, mirroring STUDIO's own new base-rotation buttons in its floating
 * JoystickOverlay (RobotDetail.tsx). Deliberately its own small composable
 * rather than a new axis baked into [Joystick3D] itself - J1 rotation
 * isn't a dx/dy/dz Cartesian delta the way every other button here is, so
 * it doesn't fit that widget's own onJog(dx,dy,dz) contract.
 *
 * @param onRotate Called with `+1`/`-1` (not a raw degree amount - the
 *   caller applies its own step size, same contract as [Joystick3D]'s
 *   own `onJog`).
 */
@Composable
fun BaseRotationButtons(
    onRotate: (direction: Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    @Composable
    fun RotateButton(direction: Int, icon: ImageVector) {
        Box(
            modifier = Modifier
                .size(padBtnSize)
                .background(Color(0xFF0F172A).copy(alpha = if (enabled) 0.8f else 0.4f), RoundedCornerShape(8.dp))
                .border(1.dp, Color(0xFFA855F7).copy(alpha = if (enabled) 0.6f else 0.15f), RoundedCornerShape(8.dp))
                .pointerInput(direction, enabled) {
                    if (!enabled) return@pointerInput
                    // Same press-and-hold-repeat contract as every other
                    // jog control in this file.
                    coroutineScope {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            val job = launch {
                                while (isActive) {
                                    onRotate(direction)
                                    delay(REPEAT_MS)
                                }
                            }
                            waitForUpOrCancellation()
                            job.cancel()
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = if (enabled) Color(0xFFA855F7) else Color(0xFFA855F7).copy(alpha = 0.4f), modifier = Modifier.size(18.dp))
        }
    }

    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        RotateButton(-1, Icons.AutoMirrored.Filled.RotateLeft)
        Text("J1", style = MaterialTheme.typography.labelSmall, color = Color(0xFFA855F7), fontWeight = FontWeight.Black, fontSize = 10.sp)
        RotateButton(1, Icons.AutoMirrored.Filled.RotateRight)
    }
}

/**
 * Just the Z column half of [Joystick3D] - see [JoystickXYPad]'s own doc
 * for why this is split out.
 */
@Composable
fun JoystickZColumn(
    onJog: (dx: Int, dy: Int, dz: Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    @Composable
    fun ZButton(
        dz: Int,
        icon: ImageVector,
        active: Boolean,
        tint: Color,
        height: androidx.compose.ui.unit.Dp,
    ) {
        Box(
            modifier = Modifier
                .width(padBtnSize)
                .height(height)
                .background(Color(0xFF0F172A).copy(alpha = if (active) 0.8f else 0.4f), RoundedCornerShape(8.dp))
                .border(1.dp, tint.copy(alpha = if (active) 0.4f else 0.15f), RoundedCornerShape(8.dp))
                .pointerInput(dz, active) {
                    if (!active) return@pointerInput
                    coroutineScope {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            val job = launch {
                                while (isActive) {
                                    onJog(0, 0, dz)
                                    delay(REPEAT_MS)
                                }
                            }
                            waitForUpOrCancellation()
                            job.cancel()
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            val labelTint = if (active) tint else tint.copy(alpha = 0.4f)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (dz > 0) Icon(icon, contentDescription = null, tint = labelTint, modifier = Modifier.size(14.dp))
                Text("Z", style = MaterialTheme.typography.labelSmall, color = labelTint, fontWeight = FontWeight.Black, fontSize = 8.sp)
                if (dz < 0) Icon(icon, contentDescription = null, tint = labelTint, modifier = Modifier.size(14.dp))
            }
        }
    }

    // Always rendered (matches STUDIO's own Joystick3D layout exactly, see
    // this file's header comment), gating is purely via `enabled`.
    Column(
        modifier = modifier.width(padBtnSize).height(padBtnSize * 3 + 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val zHeight = (padBtnSize * 3 + 8.dp - 4.dp) / 2
        ZButton(1, Icons.Filled.North, active = enabled, tint = Color(0xFF10B981), height = zHeight)
        ZButton(-1, Icons.Filled.South, active = enabled, tint = Color(0xFFF43F5E), height = zHeight)
    }
}
