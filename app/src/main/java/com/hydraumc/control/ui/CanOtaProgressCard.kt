// =============================================================================
// HYDRA-UMC CONTROL - Card with the live progress of a CAN flash cycle
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hydraumc.control.R
import com.hydraumc.control.model.CanOtaPhase
import com.hydraumc.control.model.CanOtaProgress
import com.hydraumc.control.ui.theme.metallicIndustrial

/** The string of a phase: the name of the step the board is in. */
fun canOtaPhaseText(phase: CanOtaPhase): Int = when (phase) {
    CanOtaPhase.CONNECTING -> R.string.canota_phase_connecting
    CanOtaPhase.ENTERING_BOOTLOADER -> R.string.canota_phase_entering_bootloader
    CanOtaPhase.ERASING_FRAM -> R.string.canota_phase_erasing_fram
    CanOtaPhase.TRANSFERRING -> R.string.canota_phase_transferring
    CanOtaPhase.VERIFYING -> R.string.canota_phase_verifying
    CanOtaPhase.REBOOTING -> R.string.canota_phase_rebooting
    CanOtaPhase.DONE -> R.string.canota_phase_done
    CanOtaPhase.ERROR -> R.string.canota_phase_error
}

/** Read-only: shows the phase, the pages and the percent of the flash cycle the server reports; the app starts no flash. */
@Composable
fun CanOtaProgressCard(progress: CanOtaProgress) {
    Spacer(modifier = Modifier.height(8.dp))
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .metallicIndustrial(backgroundColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = stringResource(R.string.canota_title),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = stringResource(canOtaPhaseText(progress.phase)),
            style = MaterialTheme.typography.bodyMedium,
            color = if (progress.phase == CanOtaPhase.ERROR) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )
        progress.percent?.let { percent ->
            LinearProgressIndicator(progress = { percent / 100f }, modifier = Modifier.fillMaxWidth())
            Text(text = stringResource(R.string.canota_percent, percent), style = MaterialTheme.typography.bodySmall)
        }
        val sent = progress.pagesSent
        val total = progress.pagesTotal
        if (sent != null && total != null && total > 1 && progress.phase == CanOtaPhase.TRANSFERRING) {
            Text(text = stringResource(R.string.canota_pages, sent, total), style = MaterialTheme.typography.bodySmall)
        }
        progress.error?.let {
            Text(text = stringResource(R.string.canota_error, it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}
