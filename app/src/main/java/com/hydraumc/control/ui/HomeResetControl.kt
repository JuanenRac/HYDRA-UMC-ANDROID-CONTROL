// =============================================================================
// HYDRA-UMC CONTROL - HOME/RESET button and its confirmation in two steps
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.hydraumc.control.R
import com.hydraumc.control.network.RobotCommandContract
import com.hydraumc.control.util.HomeReset
import com.hydraumc.control.viewmodel.RobotState
import com.hydraumc.control.viewmodel.RobotViewModel

/**
 * The HOME/RESET button of the Control screen. It is not drawn at all while the switch is off (the Access
 * panel turns it on). Pressing it never moves the robot: it opens the first confirmation, then the second,
 * and only the second one sends the command. A reason not to send (safe mode, playing, no permission...)
 * is shown as a phrase instead of opening anything.
 */
@Composable
fun HomeResetControl(viewModel: RobotViewModel, robot: RobotState, nowMs: Long) {
    if (!viewModel.homeResetEnabled.value) return
    // 0 = no dialog; 1.. = the number of the confirmation on screen. Driven by the shared confirmation so the
    // window of time and the order of the steps are the same the tests check.
    var dialog by remember { mutableIntStateOf(0) }
    val confirmation = viewModel.homeResetConfirmation
    val block = viewModel.homeResetBlock(robot.id, nowMs)

    if (dialog in 1..RobotCommandContract.HOME_RESET_CONFIRM_STEPS) {
        val last = dialog >= RobotCommandContract.HOME_RESET_CONFIRM_STEPS
        AlertDialog(
            onDismissRequest = { confirmation.cancel(); dialog = 0 },
            title = { Text(if (last) stringResource(R.string.home_reset_step2_title) else stringResource(R.string.home_reset_step1_title, robot.name)) },
            text = { Text(if (last) stringResource(R.string.home_reset_step2_message, robot.name) else stringResource(R.string.home_reset_step1_message)) },
            confirmButton = {
                Button(onClick = {
                    when (val progress = confirmation.advance()) {
                        is HomeReset.Confirmation.Progress.Waiting -> dialog = progress.next
                        HomeReset.Confirmation.Progress.Done -> {
                            dialog = 0
                            viewModel.homeReset()
                        }
                    }
                }) { Text(if (last) stringResource(R.string.home_reset_step2_button) else stringResource(R.string.home_reset_step1_button)) }
            },
            dismissButton = { TextButton(onClick = { confirmation.cancel(); dialog = 0 }) { Text(stringResource(R.string.cancel_button)) } },
        )
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            // A blocked button stays pressable so the person reads why; pressing it opens nothing.
            onClick = {
                if (block == HomeReset.Block.NONE) {
                    confirmation.cancel()
                    dialog = 1
                } else {
                    viewModel.lastError.value = viewModel.homeResetBlockText(block)
                }
            },
        ) { Text(stringResource(R.string.home_reset_button)) }
        if (block != HomeReset.Block.NONE) {
            Text(viewModel.homeResetBlockText(block), color = Color.Gray, style = MaterialTheme.typography.bodySmall)
        }
    }
}
