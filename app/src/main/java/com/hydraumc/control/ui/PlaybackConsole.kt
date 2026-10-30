// =============================================================================
// HYDRA-UMC CONTROL - Shared floating E-STOP/play/pause/stop console
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dangerous
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PowerOff
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.hydraumc.control.R
import androidx.compose.ui.unit.dp
import com.hydraumc.control.network.ClaimStanding
import com.hydraumc.control.util.PlayConfirmation
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
 * The big red button is the global STOP: one tap, no confirmation, every
 * robot. PLAY/PAUSE/STOP below act on the selected robot only; that per-robot
 * STOP keeps its long-press protection.
 *
 * The global STOP pulses (alpha-animated red glow, continuous,
 * independent of whether a robot is selected/online) - a real request from
 * live device testing: a static E-STOP icon read as just another button
 * among the rest, not the one control that must be found instantly under
 * stress. It is never disabled and never stops pulsing - unlike PLAY/PAUSE/
 * STOP, which stay gated on `selectedRobot.online`/`isPlaying`/`isPaused` as
 * before.
 */
@SuppressLint("MissingPermission")
internal fun vibrateOn(vibrator: Vibrator?, pattern: LongArray? = null, duration: Long = 50) {
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

/**
 * The pulsing STOP button on its own - the global STOP, one tap, no
 * confirmation: it stops every robot, not just the selected one (see
 * [RobotViewModel.globalStop]). What the Control screen (through
 * [PlaybackConsole]) and the 3D viewport show next to the one in the app bar.
 */
@Composable
fun EStopButton(viewModel: RobotViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val vibrator = remember { context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator }

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
    val stopDescription = stringResource(R.string.stop_all_description)
    Box(
        modifier = modifier
            .size(64.dp)
            .background(Color.Red.copy(alpha = pulseAlpha), CircleShape)
            .border(2.dp, pulseBorder, CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {
                    vibrateOn(vibrator, longArrayOf(0, 80, 40, 80))
                    viewModel.globalStop()
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Dangerous, contentDescription = stopDescription, tint = Color.White, modifier = Modifier.size(28.dp))
            Text(stringResource(R.string.stop_all_label), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Black)
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun PlaybackConsole(viewModel: RobotViewModel, selectedRobot: RobotState?) {
    val context = LocalContext.current
    val vibrator = remember { context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator }

    fun vibrate(pattern: LongArray? = null, duration: Long = 50) = vibrateOn(vibrator, pattern, duration)

    if (selectedRobot == null) return
    val stopHoldHint = stringResource(R.string.stop_hold_to_confirm)
    var confirmingPlay by remember { mutableStateOf(false) }
    var confirmingSafeMode by remember { mutableStateOf(false) }
    val safeModeDescription = stringResource(if (selectedRobot.isEnabled) R.string.safe_mode_disable else R.string.safe_mode_enable)
    val playDescription = stringResource(R.string.play)
    val pauseDescription = stringResource(if (selectedRobot.isPaused) R.string.resume else R.string.pause)
    val stopDescription = stringResource(R.string.stop)

    if (confirmingPlay) {
        val standing = viewModel.claimStandingOf(selectedRobot.id)
        val reservation = when (standing) {
            ClaimStanding.FREE -> stringResource(R.string.play_confirm_free)
            ClaimStanding.MINE_HERE, ClaimStanding.MINE_ELSEWHERE -> stringResource(R.string.play_confirm_mine)
            ClaimStanding.OTHER -> stringResource(R.string.play_confirm_other, viewModel.claimOf(selectedRobot.id)?.ownerUsername ?: "")
        }
        AlertDialog(
            onDismissRequest = { confirmingPlay = false },
            title = { Text(stringResource(R.string.play_confirm_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.play_confirm_message,
                        selectedRobot.name,
                        selectedRobot.recordedPointsCount,
                        PlayConfirmation.speedPercent(selectedRobot.speed),
                        reservation,
                    ),
                )
            },
            confirmButton = {
                Button(onClick = {
                    confirmingPlay = false
                    vibrate()
                    viewModel.sendCommand("play")
                }) { Text(stringResource(R.string.play_confirm_button)) }
            },
            dismissButton = { TextButton(onClick = { confirmingPlay = false }) { Text(stringResource(R.string.cancel_button)) } },
        )
    }

    if (confirmingSafeMode) {
        val disabling = selectedRobot.isEnabled
        AlertDialog(
            onDismissRequest = { confirmingSafeMode = false },
            title = { Text(stringResource(if (disabling) R.string.safe_mode_disable_title else R.string.safe_mode_enable_title)) },
            text = { Text(stringResource(if (disabling) R.string.safe_mode_disable_message else R.string.safe_mode_enable_message, selectedRobot.name)) },
            confirmButton = {
                Button(onClick = {
                    confirmingSafeMode = false
                    vibrate()
                    viewModel.sendCommand(if (disabling) "disable" else "enable")
                }) { Text(stringResource(if (disabling) R.string.safe_mode_disable_button else R.string.safe_mode_enable_button)) }
            },
            dismissButton = { TextButton(onClick = { confirmingSafeMode = false }) { Text(stringResource(R.string.cancel_button)) } },
        )
    }

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
            EStopButton(viewModel)

            // PLAY
            IconButton(
                onClick = {
                    vibrate()
                    if (PlayConfirmation.needed(viewModel.confirmPlay.value, selectedRobot.isPlaying)) confirmingPlay = true
                    else viewModel.sendCommand("play")
                },
                enabled = selectedRobot.online,
                modifier = Modifier.size(54.dp).background(if (selectedRobot.online) Color(0xFF15803D) else Color.DarkGray, CircleShape),
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = playDescription, tint = Color.White)
            }

            // PAUSE / RESUME
            IconButton(
                onClick = { vibrate(); viewModel.sendCommand("pause") },
                enabled = selectedRobot.online && selectedRobot.isPlaying,
                modifier = Modifier.size(54.dp).background(if (selectedRobot.online && selectedRobot.isPlaying) Color(0xFFB45309) else Color.DarkGray, CircleShape),
            ) {
                Icon(if (selectedRobot.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause, contentDescription = pauseDescription, tint = Color.White)
            }

            // SAFE MODE (disable / enable the robot). Anyone signed in may disable it, like a stop; only an administrator is
            // offered the enable. Both ask first.
            val safeModeAllowed = selectedRobot.online && (selectedRobot.isEnabled || viewModel.isAdmin())
            IconButton(
                onClick = { vibrate(); confirmingSafeMode = true },
                enabled = safeModeAllowed,
                modifier = Modifier.size(54.dp).background(
                    if (!safeModeAllowed) Color.DarkGray else if (selectedRobot.isEnabled) Color(0xFF475569) else Color(0xFF047857),
                    CircleShape,
                ),
            ) {
                Icon(
                    if (selectedRobot.isEnabled) Icons.Default.PowerOff else Icons.Default.PowerSettingsNew,
                    contentDescription = safeModeDescription,
                    tint = Color.White,
                )
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
                            Toast.makeText(context, stopHoldHint, Toast.LENGTH_SHORT).show()
                        },
                        onLongClick = { vibrate(); viewModel.sendCommand("stop") },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Stop, contentDescription = stopDescription, tint = Color.White)
            }
        }
    }
}
