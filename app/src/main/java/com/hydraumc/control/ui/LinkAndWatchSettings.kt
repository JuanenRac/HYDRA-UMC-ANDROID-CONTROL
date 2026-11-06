// =============================================================================
// HYDRA-UMC CONTROL - Switches of the continuous jog, the link, the watch and the pairing
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hydraumc.control.R
import com.hydraumc.control.network.TlsFingerprint
import com.hydraumc.control.viewmodel.RobotViewModel
import kotlinx.coroutines.launch

/** A switch with its label and a line of explanation. */
@Composable
private fun SwitchRow(checked: Boolean, onChange: (Boolean) -> Unit, label: String, hint: String) {
    Spacer(modifier = Modifier.height(8.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Switch(checked = checked, onCheckedChange = onChange)
        Spacer(modifier = Modifier.width(8.dp))
        Text(label, color = Color.White, style = MaterialTheme.typography.bodyMedium)
    }
    Text(hint, color = Color.Gray, style = MaterialTheme.typography.bodySmall)
}

/**
 * The switches of this app that move a robot, measure the link or talk to the watch. The jog by dead-man, the relays of the watch
 * and the pairing are off until the person turns them on; the indicator and the block on a bad link are on.
 */
@Composable
fun LinkAndWatchSettings(viewModel: RobotViewModel) {
    val controller = viewModel.deadMan
    Column(modifier = Modifier.fillMaxWidth()) {
        SwitchRow(controller.enabled.value, viewModel::setDeadManEnabled, stringResource(R.string.dead_man_switch_label), stringResource(R.string.dead_man_switch_hint))
        SwitchRow(controller.blockOnBadLink.value, viewModel::setLinkBlockOnBadLink, stringResource(R.string.link_block_switch_label), stringResource(R.string.link_block_switch_hint))
        SwitchRow(controller.indicator.value, viewModel::setLinkIndicator, stringResource(R.string.link_indicator_switch_label), stringResource(R.string.link_indicator_switch_hint))
        SwitchRow(viewModel.watchAlertsEnabled.value, viewModel::setWatchAlertsEnabled, stringResource(R.string.watch_alerts_switch_label), stringResource(R.string.watch_alerts_switch_hint))
        SwitchRow(viewModel.presenceRelayEnabled.value, viewModel::setPresenceRelayEnabled, stringResource(R.string.presence_relay_switch_label), stringResource(R.string.presence_relay_switch_hint))
        SwitchRow(viewModel.voiceConfirmRelayEnabled.value, viewModel::setVoiceConfirmRelayEnabled, stringResource(R.string.voice_confirm_switch_label), stringResource(R.string.voice_confirm_switch_hint))
        PairingSection(viewModel)
    }
}

/**
 * Pairing by QR code or link: the switch and a box to paste the link the admin page of the server shows. The phone camera
 * can also open the link straight into this app. Nothing is kept before the person confirms what the link says.
 */
@Composable
fun PairingSection(viewModel: RobotViewModel) {
    var text by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    Column(modifier = Modifier.fillMaxWidth()) {
        SwitchRow(viewModel.pairingEnabled.value, viewModel::setPairingEnabled, stringResource(R.string.pairing_switch_label), stringResource(R.string.pairing_switch_hint))
        if (viewModel.pairingEnabled.value) {
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(stringResource(R.string.pairing_paste_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedButton(
                onClick = { scope.launch { if (viewModel.receivePairing(text)) text = "" } },
                enabled = text.isNotBlank(),
            ) { Text(stringResource(R.string.pairing_paste_button)) }
        }
    }
}

/** What the link says, shown before anything is kept: the name, the address and, when there is one, the fingerprint that will be pinned. */
@Composable
fun PairingDialog(viewModel: RobotViewModel) {
    val offer = viewModel.pairingOffer.value ?: return
    AlertDialog(
        onDismissRequest = { viewModel.dismissPairing() },
        title = { Text(stringResource(R.string.pairing_dialog_title)) },
        text = {
            Column {
                Text(stringResource(R.string.pairing_dialog_message, offer.name, offer.host, offer.port))
                Spacer(modifier = Modifier.height(8.dp))
                Text(stringResource(R.string.pairing_dialog_fingerprint, TlsFingerprint.display(offer.fingerprint)), style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(4.dp))
                Text(stringResource(R.string.pairing_dialog_pin_replace), style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { Button(onClick = { viewModel.confirmPairing() }) { Text(stringResource(R.string.pairing_dialog_confirm)) } },
        dismissButton = { TextButton(onClick = { viewModel.dismissPairing() }) { Text(stringResource(R.string.cancel_button)) } },
    )
}
