// =============================================================================
// HYDRA-UMC CONTROL - The header of every screen
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
// Two rows, the same on every screen: the mark, the name and the global STOP; then the server in use (touch it to pick
// another one found on the network), the voice assistant and the account.
// =============================================================================
package com.hydraumc.control.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hydraumc.control.R
import com.hydraumc.control.network.WsStatus
import com.hydraumc.control.ui.theme.HydraColors
import com.hydraumc.control.ui.theme.StatusDot
import com.hydraumc.control.viewmodel.RobotViewModel

/** The header: the brand and the global STOP, and under them the server and the two buttons that are always at hand. */
@Composable
fun HydraTopBar(viewModel: RobotViewModel, onVoice: () -> Unit, onProfile: () -> Unit) {
    var serverMenu by remember { mutableStateOf(false) }
    val connection = viewModel.connectionState.value
    val active = viewModel.activeServer.value
    val serverName = active?.displayName?.takeIf { it.isNotBlank() } ?: viewModel.ipAddress.value
    val (dot, state) = when (connection) {
        WsStatus.CONNECTED -> HydraColors.Ok to stringResource(R.string.common_online)
        WsStatus.CONNECTING -> HydraColors.Amber to stringResource(R.string.common_connecting)
        else -> HydraColors.Amber to stringResource(R.string.common_offline)
    }
    Column(Modifier.fillMaxWidth().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 12.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Image(painter = painterResource(R.drawable.ic_hydra_logo), contentDescription = null, modifier = Modifier.size(34.dp))
            Spacer(Modifier.width(10.dp))
            // The name gives way, never the STOP: it is cut with an ellipsis on a narrow screen.
            Text(
                "HYDRA-UMC", fontSize = 18.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp, color = HydraColors.Text,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            )
            // The global STOP lives here so it is on screen on every screen.
            GlobalStopButton(viewModel)
        }
        Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                Row(
                    Modifier.clickable { serverMenu = true }.padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    StatusDot(dot)
                    Text(
                        stringResource(R.string.header_server_line, serverName, state),
                        style = MaterialTheme.typography.bodySmall, color = HydraColors.Muted,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                    )
                    Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = HydraColors.Muted)
                }
                StopDropdownMenu(expanded = serverMenu, onDismissRequest = { serverMenu = false }) {
                    val found = viewModel.discoveredServers.value
                    if (found.isEmpty()) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.no_servers_found)) }, onClick = { serverMenu = false })
                    } else {
                        found.forEach { server ->
                            DropdownMenuItem(
                                text = { Text("${server.displayName} (${server.host})") },
                                onClick = {
                                    viewModel.connectToDiscovered(server)
                                    serverMenu = false
                                },
                            )
                        }
                    }
                }
            }
            IconButton(onClick = onVoice) {
                Icon(Icons.Filled.Mic, contentDescription = stringResource(R.string.voice_assistant_title), tint = HydraColors.Accent)
            }
            IconButton(onClick = onProfile) {
                Icon(Icons.Filled.Person, contentDescription = stringResource(R.string.user_profile_title), tint = HydraColors.Accent)
            }
        }
        Spacer(Modifier.height(2.dp))
    }
}
