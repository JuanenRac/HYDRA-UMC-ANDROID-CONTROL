// =============================================================================
// HYDRA-UMC CONTROL - "More": the tools and the settings, each card explained
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hydraumc.control.R
import com.hydraumc.control.ui.theme.HydraColors
import com.hydraumc.control.ui.theme.MenuRow
import com.hydraumc.control.ui.theme.ScreenTitle

/** Everything that is not on the bottom bar. */
@Composable
fun MoreScreen(onThreeD: () -> Unit, onTelemetry: () -> Unit, onSettings: (Int) -> Unit, onAccount: () -> Unit, onAbout: () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp).padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ScreenTitle(Icons.Filled.Apps, stringResource(R.string.tab_more), stringResource(R.string.more_subtitle))
        SectionLabel(stringResource(R.string.more_tools_section))
        MenuRow(Icons.Filled.ViewInAr, stringResource(R.string.tab_3d_view), stringResource(R.string.more_3d_hint), onClick = onThreeD)
        MenuRow(Icons.Filled.Terminal, stringResource(R.string.tab_telemetry), stringResource(R.string.more_telemetry_hint), onClick = onTelemetry)
        SectionLabel(stringResource(R.string.more_settings_section))
        MenuRow(Icons.Filled.Wifi, stringResource(R.string.more_connection_title), stringResource(R.string.more_connection_hint)) { onSettings(SettingsTab.CONNECTION) }
        MenuRow(Icons.Filled.Notifications, stringResource(R.string.tab_notifications), stringResource(R.string.more_notices_hint)) { onSettings(SettingsTab.NOTICES) }
        MenuRow(Icons.Filled.Shield, stringResource(R.string.more_security_title), stringResource(R.string.more_security_hint)) { onSettings(SettingsTab.SECURITY) }
        MenuRow(Icons.Filled.Download, stringResource(R.string.common_updates), stringResource(R.string.more_updates_hint)) { onSettings(SettingsTab.UPDATES) }
        MenuRow(Icons.Filled.Person, stringResource(R.string.common_account), stringResource(R.string.more_account_hint), onClick = onAccount)
        MenuRow(Icons.Filled.Info, stringResource(R.string.about_title), stringResource(R.string.more_about_hint), onClick = onAbout)
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = HydraColors.Muted, modifier = Modifier.padding(top = 6.dp))
}
