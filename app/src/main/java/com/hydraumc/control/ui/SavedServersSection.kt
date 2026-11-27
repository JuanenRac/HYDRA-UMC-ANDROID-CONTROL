// =============================================================================
// HYDRA-UMC CONTROL - The kept servers: pick one, forget one
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hydraumc.control.R
import com.hydraumc.control.network.SavedServer
import com.hydraumc.control.ui.theme.HydraColors
import com.hydraumc.control.viewmodel.RobotViewModel

/** The kept servers in Settings: which one is in use, "Use" for the others, and "Forget", which asks first. */
@Composable
fun SavedServersSection(viewModel: RobotViewModel) {
    val servers = viewModel.savedServers.value
    if (servers.isEmpty()) return
    val current = viewModel.currentServerId
    var forgetting by remember { mutableStateOf<SavedServer?>(null) }

    Column(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.servers_saved_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.servers_saved_hint), style = MaterialTheme.typography.bodySmall, color = HydraColors.Muted)
        servers.forEach { server ->
            val inUse = server.id == current
            Row(Modifier.fillMaxWidth().testTag("saved-${server.id}"), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(server.label, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = if (inUse) FontWeight.Bold else FontWeight.Normal)
                    if (inUse) Text(stringResource(R.string.servers_current), style = MaterialTheme.typography.labelSmall, color = HydraColors.Ok)
                }
                if (!inUse) {
                    TextButton(onClick = { viewModel.selectServer(server) }) { Text(stringResource(R.string.servers_use)) }
                    TextButton(onClick = { forgetting = server }) { Text(stringResource(R.string.servers_forget)) }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }

    forgetting?.let { server ->
        StopAlertDialog(
            onDismissRequest = { forgetting = null },
            title = { Text(stringResource(R.string.servers_forget_title)) },
            text = { Text(stringResource(R.string.servers_forget_message, server.label)) },
            confirmButton = {
                TextButton(onClick = { viewModel.forgetServer(server.id); forgetting = null }) { Text(stringResource(R.string.servers_forget)) }
            },
            dismissButton = { TextButton(onClick = { forgetting = null }) { Text(stringResource(R.string.cancel_button)) } },
        )
    }
}
