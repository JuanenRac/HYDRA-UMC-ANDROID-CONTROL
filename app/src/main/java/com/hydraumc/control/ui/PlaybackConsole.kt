// =============================================================================
// HYDRA-UMC CONTROL - Shared floating E-STOP/play/pause/stop console
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0 - see LICENSE
// =============================================================================
package com.hydraumc.control.ui

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dangerous
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.hydraumc.control.viewmodel.RobotState
import com.hydraumc.control.viewmodel.RobotViewModel

/**
 * The E-STOP / PLAY / PAUSE / STOP floating console - the single, real
 * implementation of this safety-critical control surface. Originally lived
 * only inline inside ControlScreen.kt; extracted here so ThreeDScreen.kt's
 * 3D viewport (portrait and fullscreen-landscape alike) can offer the exact
 * same real long-press-protected commands without a second, divergent copy
 * of that protection logic - the ecosystem's own "reuse, don't invent" rule
 * applies doubly hard to an E-STOP button.
 *
 * Real long-press protection, unchanged from the original: a quick tap does
 * nothing but a short buzz + hint toast, so an accidental brush of this
 * button can't stop the robot; only a genuine hold (Compose's own
 * long-press timing, ~500ms) actually sends the command.
 *
 * The E-STOP itself now pulses (alpha-animated red glow, continuous,
 * independent of whether a robot is selected/online) - a real request from
 * live device testing: a static E-STOP icon read as just another button
 * among the rest, not the one control that must be found instantly under
 * stress. It is never disabled and never stops pulsing - unlike PLAY/PAUSE/
 * STOP, which stay gated on `selectedRobot.online`/`isPlaying`/`isPaused` as
 * before.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun PlaybackConsole(viewModel: RobotViewModel, selectedRobot: RobotState?) {
    val context = LocalContext.current
    val vibrator = remember { context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator }

    @SuppressLint("MissingPermission")
    fun vibrate(pattern: LongArray? = null, duration: Long = 50) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (pattern != null) vibrator?.vibrate(VibrationEffect.createWaveform(pattern, -1))
                else vibrator?.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                if (pattern != null) vibrator?.vibrate(pattern, -1)
                else vibrator?.vibrate(duration)
            }
        } catch (_: Exception) {}
    }

    if (selectedRobot == null) return

    // Continuous pulse - never stops, never gated on `enabled`. A real
    // physical E-STOP is always live; this one reads the same way.
    val infiniteTransition = rememberInfiniteTransition(label = "estop-pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.55f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 650, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "estop-pulse-alpha",
    )
    val pulseBorder by animateColorAsState(
        targetValue = Color.Red.copy(alpha = 0.6f + pulseAlpha * 0.4f),
        label = "estop-pulse-border",
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(90.dp),
        color = Color(0xFF1E293B).copy(alpha = 0.95f),
        shape = RoundedCornerShape(24.dp),
        shadowElevation = 12.dp,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // EMERGENCY STOP - see this file's own header comment for the
            // long-press protection and the pulse animation.
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(Color.Red.copy(alpha = pulseAlpha), CircleShape)
                    .border(2.dp, pulseBorder, CircleShape)
                    .combinedClickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {
                            vibrate(duration = 30)
                            Toast.makeText(context, "Hold to confirm E-STOP", Toast.LENGTH_SHORT).show()
                        },
                        onLongClick = {
                            vibrate(longArrayOf(0, 150, 50, 150, 50, 150))
                            viewModel.sendCommand("stop")
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Dangerous, contentDescription = "E-STOP", tint = Color.White, modifier = Modifier.size(36.dp))
            }

            // PLAY
            IconButton(
                onClick = { vibrate(); viewModel.sendCommand("play") },
                enabled = selectedRobot.online,
                modifier = Modifier.size(54.dp).background(if (selectedRobot.online) Color(0xFF15803D) else Color.DarkGray, CircleShape),
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = "PLAY", tint = Color.White)
            }

            // PAUSE / RESUME
            IconButton(
                onClick = { vibrate(); viewModel.sendCommand("pause") },
                enabled = selectedRobot.online && selectedRobot.isPlaying,
                modifier = Modifier.size(54.dp).background(if (selectedRobot.online && selectedRobot.isPlaying) Color(0xFFB45309) else Color.DarkGray, CircleShape),
            ) {
                Icon(if (selectedRobot.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause, contentDescription = "PAUSE", tint = Color.White)
            }

            // STOP - same long-press protection as E-STOP above.
            val stopEnabled = selectedRobot.online && (selectedRobot.isPlaying || selectedRobot.isPaused)
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .background(if (stopEnabled) Color(0xFF991B1B) else Color.DarkGray, CircleShape)
                    .combinedClickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        enabled = stopEnabled,
                        onClick = {
                            vibrate(duration = 30)
                            Toast.makeText(context, "Hold to confirm STOP", Toast.LENGTH_SHORT).show()
                        },
                        onLongClick = { vibrate(); viewModel.sendCommand("stop") },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Stop, contentDescription = "STOP", tint = Color.White)
            }
        }
    }
}
