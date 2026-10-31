// =============================================================================
// HYDRA-UMC CONTROL - who connects to the server
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
// A simple view of the apps the server has seen (kind, user, version, sessions open now, sign-ins that worked or were
// refused) and the latest sign-ins and sign-outs. The server gives an administrator every entry and any other account only
// its own; it holds no password or token in either list. An address is shown only when the server sends one.
package com.hydraumc.control.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hydraumc.control.R
import com.hydraumc.control.network.ConnectionClient
import com.hydraumc.control.network.ConnectionEvent
import com.hydraumc.control.network.ConnectionHistory
import com.hydraumc.control.network.ConnectionsFailure
import com.hydraumc.control.viewmodel.RobotViewModel
import kotlinx.coroutines.delay

@StringRes
private fun kindRes(kind: String): Int = when (kind) {
    "studio" -> R.string.connections_kind_studio
    "suite" -> R.string.connections_kind_suite
    "android" -> R.string.connections_kind_android
    "ios" -> R.string.connections_kind_ios
    "dsi" -> R.string.connections_kind_dsi
    "watch" -> R.string.connections_kind_watch
    "voice" -> R.string.connections_kind_voice
    else -> R.string.connections_kind_other
}

@StringRes
private fun actionRes(action: String): Int = when (action) {
    "first_seen" -> R.string.connections_action_first_seen
    "login" -> R.string.connections_action_login
    "login_failed" -> R.string.connections_action_login_failed
    "second_factor_failed" -> R.string.connections_action_second_factor_failed
    "logout" -> R.string.connections_action_logout
    "session_ended" -> R.string.connections_action_session_ended
    else -> R.string.connections_action_session_expired
}

@StringRes
private fun failureRes(failure: ConnectionsFailure): Int = when (failure) {
    ConnectionsFailure.UNAVAILABLE -> R.string.connections_failure_unavailable
    ConnectionsFailure.SESSION -> R.string.connections_failure_session
    ConnectionsFailure.NETWORK -> R.string.connections_failure_network
    ConnectionsFailure.OTHER -> R.string.connections_failure_other
}

@Composable
private fun agoText(thenMs: Long, nowMs: Long): String {
    val (amount, unit) = ConnectionHistory.ago(thenMs, nowMs)
    val res = when (unit) {
        "s" -> R.string.connections_ago_seconds
        "min" -> R.string.connections_ago_minutes
        "h" -> R.string.connections_ago_hours
        else -> R.string.connections_ago_days
    }
    return stringResource(res, amount.toInt())
}

@Composable
fun ConnectionsDialog(viewModel: RobotViewModel, onDismiss: () -> Unit) {
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    // Read when the dialog opens; the clock only moves the "ago" texts forward.
    LaunchedEffect(Unit) { viewModel.refreshConnections() }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            nowMs = System.currentTimeMillis()
        }
    }

    val clients = viewModel.connectionClients.value
    val events = viewModel.connectionEvents.value
    val failure = viewModel.connectionsFailure.value

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.connections_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.connections_intro), style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                Spacer(modifier = Modifier.height(8.dp))
                if (failure != null) {
                    Text(stringResource(failureRes(failure)), color = Color(0xFFF87171), fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                }
                Text(stringResource(R.string.connections_clients), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                if (clients != null && clients.isEmpty()) {
                    Text(stringResource(R.string.connections_none_clients), color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                }
                clients?.forEach { ClientRow(it, nowMs) }
                Spacer(modifier = Modifier.height(10.dp))
                Text(stringResource(R.string.connections_events), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                if (events != null && events.isEmpty()) {
                    Text(stringResource(R.string.connections_none_events), color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                }
                events?.forEach { EventRow(it, nowMs) }
            }
        },
        dismissButton = {
            TextButton(onClick = { viewModel.refreshConnections() }, enabled = !viewModel.connectionsBusy.value) {
                Text(stringResource(R.string.connections_refresh))
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close_button)) } },
    )
}

@Composable
private fun ClientRow(client: ConnectionClient, nowMs: Long) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(client.user ?: stringResource(R.string.connections_no_user), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
            if (client.twoFactor == true) {
                Text(stringResource(R.string.connections_two_factor), color = Color(0xFF10B981), style = MaterialTheme.typography.labelSmall)
            }
        }
        val kind = stringResource(kindRes(client.kind))
        val origin = when (client.origin) {
            "local" -> stringResource(R.string.connections_origin_local)
            "remote" -> stringResource(R.string.connections_origin_remote)
            else -> null
        }
        val line = listOfNotNull("$kind${client.appVersion?.let { " $it" } ?: ""}", origin, client.ip).joinToString(" - ")
        Text(line, color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
        Text(
            stringResource(R.string.connections_sessions, client.activeSessions) + "  " +
                stringResource(R.string.connections_logins, client.loginsOk, client.loginsFailed) + "  " +
                agoText(client.lastSeenMs, nowMs),
            color = if (client.activeSessions > 0) Color(0xFF10B981) else Color.Gray,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
private fun EventRow(event: ConnectionEvent, nowMs: Long) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(actionRes(event.action)),
                color = if (event.isFailure) Color(0xFFF87171) else Color.White,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodySmall,
            )
            Text(agoText(event.atMs, nowMs), color = Color.Gray, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelSmall)
        }
        val who = (event.user ?: stringResource(R.string.connections_no_user)) +
            " (" + stringResource(kindRes(event.kind)) + (event.appVersion?.let { " $it" } ?: "") + ")"
        Text(who, color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
    }
}
