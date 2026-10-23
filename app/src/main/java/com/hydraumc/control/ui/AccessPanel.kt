// =============================================================================
// HYDRA-UMC CONTROL - reservation and jog arming panel of the control screen
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
// Shows who holds the selected robot (nobody, this phone, the same account on another device, or
// another account), lets the operator reserve and release it, lists every reservation of the
// server and, only when the administrator requires it, arms the jog before it moves the robot.
// Reservations are optional: a robot nobody reserved is commanded as before.
package com.hydraumc.control.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hydraumc.control.R
import com.hydraumc.control.network.ClaimStanding
import com.hydraumc.control.viewmodel.RobotViewModel

@Composable
fun AccessPanel(viewModel: RobotViewModel, robotId: Int, nowMs: Long) {
    val standing = viewModel.claimStandingOf(robotId)
    val claim = viewModel.claimOf(robotId)
    val busy = viewModel.accessBusy.value
    val safety = viewModel.safetyByRobot.value[robotId]
    var showList by remember { mutableStateOf(false) }
    var confirmTakeOver by remember { mutableStateOf(false) }
    val canTakeOver = viewModel.canTakeOver(robotId)

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                stringResource(R.string.access_title).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray,
                fontWeight = FontWeight.Black,
            )
            Spacer(modifier = Modifier.height(4.dp))
            val statusText = when (standing) {
                ClaimStanding.FREE -> stringResource(R.string.access_free)
                ClaimStanding.MINE_HERE -> stringResource(R.string.access_mine_here)
                ClaimStanding.MINE_ELSEWHERE -> stringResource(R.string.access_mine_elsewhere)
                ClaimStanding.OTHER -> stringResource(R.string.access_other, claim?.ownerUsername ?: "")
            }
            Text(
                statusText,
                color = when (standing) {
                    ClaimStanding.FREE -> Color.LightGray
                    ClaimStanding.MINE_HERE -> Color(0xFF10B981)
                    else -> Color(0xFFF59E0B)
                },
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { viewModel.claimSelectedRobot() },
                    enabled = !busy && standing == ClaimStanding.FREE,
                ) { Text(stringResource(R.string.access_reserve)) }
                OutlinedButton(
                    onClick = { viewModel.releaseSelectedRobot() },
                    enabled = !busy && standing == ClaimStanding.MINE_HERE,
                ) { Text(stringResource(R.string.access_release)) }
                TextButton(onClick = { showList = !showList }) { Text(stringResource(R.string.access_list_show)) }
            }
            // Only for an administrator, only on a robot someone else holds, and never without asking first.
            if (canTakeOver) {
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedButton(onClick = { confirmTakeOver = true }, enabled = !busy) {
                    Text(stringResource(R.string.access_take_over))
                }
            }

            if (showList) {
                val all = viewModel.claims.value
                if (all.isEmpty()) {
                    Text(stringResource(R.string.access_list_empty), color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                } else {
                    all.forEach { c ->
                        Text(
                            stringResource(R.string.access_list_item, c.robotName.ifBlank { "#${c.robotId}" }, c.ownerUsername),
                            color = Color.LightGray,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }

            // The jog arming exists only when the administrator turned it on in the server.
            if (safety?.armRequired == true) {
                Spacer(modifier = Modifier.height(12.dp))
                val armedUntil = viewModel.jogArmedUntilMs.value[robotId] ?: 0L
                val armed = armedUntil > nowMs
                Text(
                    if (armed) stringResource(R.string.jog_armed, ((armedUntil - nowMs + 999) / 1000).toInt())
                    else stringResource(R.string.jog_not_armed),
                    color = if (armed) Color(0xFF10B981) else Color(0xFFF59E0B),
                    fontWeight = FontWeight.Bold,
                )
                Text(stringResource(R.string.jog_arm_hint), color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(6.dp))
                Button(onClick = { viewModel.setJogArmed(!armed) }, enabled = !busy) {
                    Text(stringResource(if (armed) R.string.jog_disarm else R.string.jog_arm))
                }
            }
        }
    }

    if (confirmTakeOver && canTakeOver) {
        AlertDialog(
            onDismissRequest = { confirmTakeOver = false },
            title = { Text(stringResource(R.string.access_take_over_title)) },
            text = { Text(stringResource(R.string.access_take_over_message, claim?.ownerUsername ?: "")) },
            confirmButton = {
                Button(onClick = {
                    confirmTakeOver = false
                    viewModel.takeOverSelectedRobot()
                }) { Text(stringResource(R.string.access_take_over_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmTakeOver = false }) { Text(stringResource(R.string.cancel_button)) }
            },
        )
    }
}
