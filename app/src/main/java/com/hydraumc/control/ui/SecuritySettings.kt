// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - Settings > Security: optional, configurable TLS
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.hydraumc.control.network.TlsMode
import com.hydraumc.control.network.TlsSettings
import com.hydraumc.control.ui.theme.HydraButton
import com.hydraumc.control.viewmodel.RobotViewModel

/**
 * TLS choices: how to reach the server (automatic, TLS required, plain only), an optional pinned
 * certificate fingerprint and an optional CA. The default is the behaviour the app always had.
 */
@Composable
fun SecuritySettings(viewModel: RobotViewModel) {
    val saved = viewModel.tlsSettings.value
    var mode by remember(saved) { mutableStateOf(saved.mode) }
    var pin by remember(saved) { mutableStateOf(saved.pinnedFingerprint) }
    var ca by remember(saved) { mutableStateOf(saved.customCaPem) }
    var plainRemote by remember(saved) { mutableStateOf(saved.allowPlainRemote) }
    val message = viewModel.tlsSettingsMessage.value

    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        Text(stringResource(R.string.tls_title), style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(16.dp))

        TlsModeRow(mode == TlsMode.AUTO, R.string.tls_mode_auto, R.string.tls_mode_auto_hint) { mode = TlsMode.AUTO }
        TlsModeRow(mode == TlsMode.REQUIRED, R.string.tls_mode_required, R.string.tls_mode_required_hint) { mode = TlsMode.REQUIRED }
        TlsModeRow(mode == TlsMode.OFF, R.string.tls_mode_off, R.string.tls_mode_off_hint) { mode = TlsMode.OFF }

        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = pin,
            onValueChange = { pin = it },
            label = { Text(stringResource(R.string.tls_pin_label)) },
            modifier = Modifier.fillMaxWidth(),
            enabled = mode != TlsMode.OFF,
        )
        Text(stringResource(R.string.tls_pin_hint), style = MaterialTheme.typography.bodySmall, color = Color.Gray)

        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = ca,
            onValueChange = { ca = it },
            label = { Text(stringResource(R.string.tls_ca_label)) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
            maxLines = 8,
            enabled = mode != TlsMode.OFF,
        )
        Text(stringResource(R.string.tls_ca_hint), style = MaterialTheme.typography.bodySmall, color = Color.Gray)

        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Switch(checked = plainRemote, onCheckedChange = { plainRemote = it })
            Column {
                Text(stringResource(R.string.tls_plain_remote_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.tls_plain_remote_hint), style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        HydraButton(
            text = stringResource(R.string.tls_save),
            onClick = { viewModel.saveTlsSettings(TlsSettings(mode, pin, ca, plainRemote)) },
            modifier = Modifier.fillMaxWidth(),
        )
        if (message != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(message, style = MaterialTheme.typography.bodySmall)
        }

        // The access options of the server itself: only an administrator can read or change them.
        if (viewModel.isAdmin()) ServerOptionsSection(viewModel)
    }
}

@Composable
private fun TlsModeRow(selected: Boolean, title: Int, hint: Int, onSelect: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Column {
            Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(hint), style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        }
    }
}
