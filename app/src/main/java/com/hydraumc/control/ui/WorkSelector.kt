// =============================================================================
// HYDRA-UMC CONTROL - Work (trajectory) selector of the Control screen
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.unit.dp
import com.hydraumc.control.R
import com.hydraumc.control.util.WorkFiles
import com.hydraumc.control.viewmodel.RobotState
import com.hydraumc.control.viewmodel.RobotViewModel

/**
 * Shows the Work (trajectory file) loaded in the selected robot and lets the person pick another from the robot's folder on the
 * server. Picking one loads its points with the `trajectory` command; it never starts the playback, and a robot that is
 * playing, in safe mode or offline is refused with a phrase.
 */
@Composable
fun WorkSelector(viewModel: RobotViewModel, robot: RobotState) {
    var open by remember { mutableStateOf(false) }
    val selected = viewModel.state.robotById(robot.id)?.raw?.optString("selectedWorkFile", "").orEmpty()
    val block = viewModel.workBlock(robot.id)
    Column(modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                if (block == WorkFiles.Block.NONE) {
                    open = true
                    viewModel.loadWorkList(robot.id)
                } else {
                    viewModel.lastError.value = viewModel.workBlockText(block)
                }
            },
        ) { Text(stringResource(R.string.work_selector_label, selected.ifEmpty { stringResource(R.string.work_none) })) }
        if (block != WorkFiles.Block.NONE) {
            Text(viewModel.workBlockText(block), color = Color.Gray, style = MaterialTheme.typography.bodySmall)
        }
    }
    if (open) {
        val files = viewModel.workFiles.value
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(stringResource(R.string.work_dialog_title)) },
            text = {
                when {
                    viewModel.workBusy.value && files == null -> Text(stringResource(R.string.work_loading))
                    files.isNullOrEmpty() -> Text(stringResource(R.string.work_empty))
                    else -> LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                        items(files) { file ->
                            Text(
                                file,
                                color = if (file == selected) Color(0xFF0EA5E9) else Color.White,
                                modifier = Modifier.fillMaxWidth().clickable {
                                    open = false
                                    viewModel.selectWork(robot.id, file)
                                }.padding(vertical = 12.dp),
                            )
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { viewModel.loadWorkList(robot.id) }) { Text(stringResource(R.string.work_refresh)) } },
            dismissButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.cancel_button)) } },
        )
    }
}
