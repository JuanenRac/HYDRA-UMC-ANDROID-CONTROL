// =============================================================================
// HYDRA-UMC CONTROL - access options of the server (administrators only)
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
// Seven switches of the server with where each value comes from. A value fixed by the environment of the server is locked, a
// change that lowers the protection is asked about first, and a server that predates an option simply does not show it.
package com.hydraumc.control.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hydraumc.control.R
import com.hydraumc.control.network.OptionSource
import com.hydraumc.control.network.ProtectionLowering
import com.hydraumc.control.network.ServerOption
import com.hydraumc.control.network.ServerOptionsFailure
import com.hydraumc.control.network.ServerSecurity
import com.hydraumc.control.network.ServerSecurityValues
import com.hydraumc.control.ui.theme.HydraButton
import com.hydraumc.control.viewmodel.RobotViewModel

@StringRes
internal fun failureRes(failure: ServerOptionsFailure): Int = when (failure) {
    ServerOptionsFailure.UNAVAILABLE -> R.string.server_options_failure_unavailable
    ServerOptionsFailure.SESSION -> R.string.server_options_failure_session
    ServerOptionsFailure.FORBIDDEN -> R.string.server_options_failure_forbidden
    ServerOptionsFailure.NETWORK -> R.string.server_options_failure_network
    ServerOptionsFailure.OTHER -> R.string.server_options_failure_other
}

@StringRes
internal fun loweringRes(lowering: ProtectionLowering): Int = when (lowering) {
    ProtectionLowering.HEALTH_OPEN -> R.string.server_options_health_confirm
    ProtectionLowering.RECORD_ADDRESS -> R.string.server_options_address_confirm
    ProtectionLowering.CAMERA_OPEN -> R.string.server_options_confirm_camera
    ProtectionLowering.WS_QUERY_TOKEN -> R.string.server_options_confirm_ws
    ProtectionLowering.LOG_REDACTION_OFF -> R.string.server_options_confirm_log
    ProtectionLowering.VOICE_SIGNATURE_OFF -> R.string.server_options_confirm_voice
}

@StringRes
private fun sourceRes(source: OptionSource): Int = when (source) {
    OptionSource.ENV -> R.string.server_options_source_env
    OptionSource.CONFIG -> R.string.server_options_source_config
    OptionSource.DEFAULT -> R.string.server_options_source_default
}

@Composable
private fun OptionRow(option: ServerOption?, checked: Boolean, @StringRes label: Int, @StringRes help: Int?, onChange: (Boolean) -> Unit) {
    if (option == null) return
    Spacer(modifier = Modifier.height(12.dp))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Switch(checked = checked, onCheckedChange = onChange, enabled = !option.locked)
        Column {
            Text(stringResource(label), style = MaterialTheme.typography.titleMedium)
            if (help != null) Text(stringResource(help), style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            Text(stringResource(sourceRes(option.source)), style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        }
    }
}

/** The section of the security screen for an administrator: reads the options on opening and saves only what changed. */
@Composable
fun ServerOptionsSection(viewModel: RobotViewModel) {
    val options = viewModel.serverOptions.value
    val failure = viewModel.serverOptionsFailure.value
    val busy = viewModel.serverOptionsBusy.value
    var values by remember(options) { mutableStateOf(options?.toValues()) }
    var asking by remember { mutableStateOf<List<ProtectionLowering>?>(null) }

    LaunchedEffect(Unit) { viewModel.refreshServerOptions() }

    Spacer(modifier = Modifier.height(24.dp))
    Text(stringResource(R.string.server_options_title), style = MaterialTheme.typography.titleLarge)
    Text(stringResource(R.string.server_options_intro), style = MaterialTheme.typography.bodySmall, color = Color.Gray)

    if (failure != null) {
        Spacer(modifier = Modifier.height(8.dp))
        Text(stringResource(failureRes(failure)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
    OutlinedButton(onClick = { viewModel.refreshServerOptions() }, enabled = !busy) { Text(stringResource(R.string.connections_refresh)) }

    val current = options
    val shown = values
    if (current != null && shown != null) {
        if (current.isEmpty) {
            Text(stringResource(R.string.server_options_none), style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            return
        }
        OptionRow(current.healthOpen, shown.healthOpen, R.string.server_options_health_label, R.string.server_options_health_help) { values = shown.copy(healthOpen = it) }
        OptionRow(current.publishGlobalStop, shown.publishGlobalStop, R.string.server_options_stop_label, R.string.server_options_stop_help) { values = shown.copy(publishGlobalStop = it) }
        OptionRow(current.recordAddress, shown.recordAddress, R.string.server_options_address_label, R.string.server_options_address_help) { values = shown.copy(recordAddress = it) }
        OptionRow(current.cameraOpen, shown.cameraOpen, R.string.server_options_camera_label, R.string.server_options_exposure_help) { values = shown.copy(cameraOpen = it) }
        OptionRow(current.wsQueryToken, shown.wsQueryToken, R.string.server_options_ws_label, null) { values = shown.copy(wsQueryToken = it) }
        OptionRow(current.logRedaction, shown.logRedaction, R.string.server_options_log_label, null) { values = shown.copy(logRedaction = it) }
        OptionRow(current.voiceSignature, shown.voiceSignature, R.string.server_options_voice_label, null) { values = shown.copy(voiceSignature = it) }

        Spacer(modifier = Modifier.height(16.dp))
        HydraButton(
            text = stringResource(R.string.server_options_save),
            onClick = {
                val lowering = ServerSecurity.lowerings(current, shown)
                if (lowering.isEmpty()) viewModel.saveServerOptions(shown) else asking = lowering
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !busy && ServerSecurity.patch(current, shown) != null,
        )
        if (viewModel.serverOptionsSaved.value && failure == null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(stringResource(R.string.server_options_saved), style = MaterialTheme.typography.bodySmall)
        }
    }

    asking?.let { list ->
        AlertDialog(
            onDismissRequest = { asking = null },
            title = { Text(stringResource(R.string.server_options_confirm_title)) },
            text = { Column { list.forEach { Text("- " + stringResource(loweringRes(it))) } } },
            confirmButton = {
                TextButton(onClick = {
                    asking = null
                    values?.let { viewModel.saveServerOptions(it) }
                }) { Text(stringResource(R.string.server_options_confirm_yes)) }
            },
            dismissButton = { TextButton(onClick = { asking = null }) { Text(stringResource(R.string.cancel_button)) } },
        )
    }
}
