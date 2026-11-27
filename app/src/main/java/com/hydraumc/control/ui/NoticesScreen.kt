// =============================================================================
// HYDRA-UMC CONTROL - The notices screen: what happened, when and what to do
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hydraumc.control.R
import com.hydraumc.control.ui.theme.HydraColors
import com.hydraumc.control.ui.theme.IconBadge
import com.hydraumc.control.ui.theme.Panel
import com.hydraumc.control.ui.theme.ScreenTitle
import com.hydraumc.control.util.Notice
import com.hydraumc.control.util.NoticeGroup
import com.hydraumc.control.util.NoticeKind
import com.hydraumc.control.util.Notices
import java.text.DateFormat
import java.util.Date

@Composable
fun NoticesScreen(notices: List<Notice>, onMarkSeen: () -> Unit, onSettings: () -> Unit, server: ServerRecordUi? = null) {
    var filter by rememberSaveable { mutableStateOf<NoticeGroup?>(null) }
    // The phone's own list first; the server is asked only when its part is opened.
    var ofServer by rememberSaveable { mutableStateOf(false) }
    if (server != null && ofServer) {
        androidx.compose.runtime.LaunchedEffect(Unit) { server.onRefresh() }
    }
    val unseen = Notices.unseen(notices)
    val shown = notices.filter { filter == null || it.kind.group == filter }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp).padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ScreenTitle(
            Icons.Filled.Notifications, stringResource(R.string.tab_notices),
            if (unseen > 0) stringResource(R.string.notices_attention, unseen) else stringResource(R.string.notices_calm),
        ) {
            if (notices.any { !it.seen }) TextButton(onClick = onMarkSeen) { Text(stringResource(R.string.notices_mark_seen), color = HydraColors.Accent) }
        }
        if (server != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !ofServer, onClick = { ofServer = false }, label = { Text(stringResource(R.string.record_tab_phone)) })
                FilterChip(selected = ofServer, onClick = { ofServer = true }, label = { Text(stringResource(R.string.record_tab_server)) })
            }
            if (ofServer) {
                ServerRecordView(server)
                return@Column
            }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val filters = listOf(
                null to R.string.notices_filter_all, NoticeGroup.ROBOTS to R.string.common_robots,
                NoticeGroup.SERVER to R.string.notices_filter_server, NoticeGroup.JOBS to R.string.notices_filter_jobs,
            )
            filters.forEach { (group, label) ->
                FilterChip(selected = filter == group, onClick = { filter = group }, label = { Text(stringResource(label)) })
            }
        }
        if (shown.isEmpty()) {
            Panel(Modifier.fillMaxWidth(), tint = HydraColors.Ok) {
                Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    IconBadge(Icons.Filled.Check, HydraColors.Ok, size = 52.dp)
                    Column {
                        Text(stringResource(R.string.notices_calm), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.notices_calm_hint), style = MaterialTheme.typography.bodySmall, color = HydraColors.Muted)
                    }
                }
            }
        }
        shown.forEach { NoticeRow(it) }
        TextButton(onClick = onSettings, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Icon(Icons.Filled.Settings, contentDescription = null, tint = HydraColors.Accent)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.notices_settings_link), color = HydraColors.Accent)
        }
    }
}

private data class Look(val icon: ImageVector, val tint: Color)

@Composable
private fun NoticeRow(notice: Notice) {
    val look = when (notice.kind) {
        NoticeKind.CONNECTION_LOST -> Look(Icons.Filled.CloudOff, HydraColors.Amber)
        NoticeKind.CONNECTION_RESTORED -> Look(Icons.Filled.Check, HydraColors.Ok)
        NoticeKind.ROBOT_OFFLINE -> Look(Icons.Filled.Warning, HydraColors.Amber)
        NoticeKind.ROBOT_SAFE_MODE -> Look(Icons.Filled.Warning, HydraColors.Alert)
        NoticeKind.STOP_NOT_CONFIRMED -> Look(Icons.Filled.Dangerous, HydraColors.Alert)
        NoticeKind.STOP_CONFIRMED -> Look(Icons.Filled.Shield, HydraColors.Ok)
        NoticeKind.JOB_DONE -> Look(Icons.Filled.Check, HydraColors.Ok)
        NoticeKind.SERVER_MESSAGE -> Look(Icons.Filled.Dns, HydraColors.Amber)
        NoticeKind.UPDATE -> Look(Icons.Filled.Download, HydraColors.Accent)
    }
    val title = when (notice.kind) {
        NoticeKind.CONNECTION_LOST -> stringResource(R.string.watch_alert_connection_lost)
        NoticeKind.CONNECTION_RESTORED -> stringResource(R.string.watch_alert_connection_restored)
        NoticeKind.ROBOT_OFFLINE -> stringResource(R.string.watch_alert_robot_offline, notice.detail)
        NoticeKind.ROBOT_SAFE_MODE -> stringResource(R.string.watch_alert_robot_safe_mode, notice.detail)
        NoticeKind.STOP_NOT_CONFIRMED -> stringResource(R.string.watch_alert_stop_not_confirmed)
        NoticeKind.STOP_CONFIRMED -> stringResource(R.string.watch_alert_stop_confirmed)
        NoticeKind.JOB_DONE -> stringResource(R.string.notices_job_done, notice.detail)
        NoticeKind.SERVER_MESSAGE -> notice.detail
        NoticeKind.UPDATE -> stringResource(R.string.notices_update, notice.detail)
    }
    val fresh = !notice.seen && notice.kind.attention
    Panel(Modifier.fillMaxWidth(), tint = if (fresh) look.tint else null) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconBadge(look.icon, look.tint, size = 40.dp)
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = if (fresh) FontWeight.SemiBold else FontWeight.Normal)
                Text(
                    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(notice.atMs)),
                    style = MaterialTheme.typography.bodySmall, color = HydraColors.Muted,
                )
            }
        }
    }
}
