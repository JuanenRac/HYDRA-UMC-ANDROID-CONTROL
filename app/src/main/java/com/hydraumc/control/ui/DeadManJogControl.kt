// =============================================================================
// HYDRA-UMC CONTROL - Continuous jog held by a dead-man and the link indicator
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hydraumc.control.R
import com.hydraumc.control.network.RobotCommandContract
import com.hydraumc.control.util.LinkHealth
import com.hydraumc.control.viewmodel.RobotState
import com.hydraumc.control.viewmodel.RobotViewModel

/** The colour and the word of each level of the link. */
private fun levelColor(level: LinkHealth.Level): Color = when (level) {
    LinkHealth.Level.GOOD -> Color(0xFF10B981)
    LinkHealth.Level.POOR -> Color(0xFFF59E0B)
    LinkHealth.Level.BAD, LinkHealth.Level.LOST -> Color(0xFFEF4444)
    LinkHealth.Level.UNKNOWN -> Color.Gray
}

/**
 * A small indicator of the health of the link to the server: a coloured dot, the word for the level and the last
 * round-trip time. It is not drawn when the person switched it off in the Access panel.
 */
@Composable
fun LinkHealthChip(viewModel: RobotViewModel, modifier: Modifier = Modifier) {
    val controller = viewModel.deadMan
    if (!controller.indicator.value) return
    val level = controller.linkLevel.value
    val rtt = controller.linkRttMs.value
    val word = when (level) {
        LinkHealth.Level.GOOD -> R.string.link_good
        LinkHealth.Level.POOR -> R.string.link_poor
        LinkHealth.Level.BAD -> R.string.link_bad
        LinkHealth.Level.LOST -> R.string.link_lost
        LinkHealth.Level.UNKNOWN -> R.string.link_unknown
    }
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(10.dp).background(levelColor(level), CircleShape))
        Spacer(modifier = Modifier.width(6.dp))
        Text(stringResource(R.string.link_label) + ": " + stringResource(word), color = Color.White, style = MaterialTheme.typography.bodySmall)
        if (rtt != null && level != LinkHealth.Level.LOST) {
            Spacer(modifier = Modifier.width(6.dp))
            Text(stringResource(R.string.link_rtt_ms, rtt.toInt()), color = Color.Gray, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** One control that is held: it starts on touch and stops the moment the finger lifts, slides off or the screen is left. */
@Composable
private fun HoldButton(label: String, held: Boolean, onPress: () -> Unit, onRelease: () -> Unit, modifier: Modifier = Modifier) {
    DisposableEffect(Unit) { onDispose { onRelease() } }
    Box(
        modifier = modifier
            .height(52.dp)
            .background(if (held) Color(0xFF0EA5E9) else Color(0xFF1E293B), RoundedCornerShape(12.dp))
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    onPress()
                    // Returns when the finger lifts, slides off or the gesture is cancelled: either way the control is released.
                    tryAwaitRelease()
                    onRelease()
                })
            },
        contentAlignment = Alignment.Center,
    ) { Text(label, color = Color.White, style = MaterialTheme.typography.titleMedium) }
}

/**
 * The continuous jog: an axis moves while its button is held. It is not drawn while the person has not switched it on
 * (Access panel). The server stops the axis by itself when the heartbeat stops, and so does the app (see DeadManJog).
 */
@Composable
fun DeadManJogControl(viewModel: RobotViewModel, robot: RobotState, target: String) {
    val controller = viewModel.deadMan
    if (!controller.enabled.value) return
    val held = robot.id in controller.held.value
    val block = controller.blockOf(robot.id)
    val axes = if (target == "xytable") listOf("x", "y") else listOf("x", "y", "z")
    Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.dead_man_title).uppercase(), style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        Text(stringResource(R.string.dead_man_speed_label, controller.velocity.value.toInt()), color = Color.White, style = MaterialTheme.typography.bodySmall)
        Slider(
            value = controller.velocity.value,
            onValueChange = { controller.velocity.value = it },
            valueRange = RobotCommandContract.DEAD_MAN_JOG_VELOCITY_MIN.toFloat()..RobotCommandContract.DEAD_MAN_JOG_VELOCITY_MAX.toFloat(),
            // Not while a control is held: the speed of a jog that is running does not change under the finger.
            enabled = !held,
        )
        for (axis in axes) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                for (direction in listOf(-1, 1)) {
                    HoldButton(
                        label = (if (direction < 0) "-" else "+") + axis.uppercase(),
                        held = held,
                        onPress = { controller.press(robot.id, target, axis, direction) },
                        onRelease = { controller.release(robot.id) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        Text(stringResource(R.string.dead_man_hint), color = Color.Gray, style = MaterialTheme.typography.bodySmall)
        if (block != com.hydraumc.control.util.DeadManJog.Block.NONE && !held) {
            Text(viewModel.deadManBlockText(block), color = Color(0xFFF59E0B), style = MaterialTheme.typography.bodySmall)
        }
    }
}
