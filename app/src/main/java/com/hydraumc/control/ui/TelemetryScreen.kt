// =============================================================================
// HYDRA-UMC CONTROL - UI screen for displaying real-time system telemetry logs
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0 - see LICENSE
// =============================================================================
package com.hydraumc.control.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hydraumc.control.R
import com.hydraumc.control.viewmodel.EcosystemProject
import com.hydraumc.control.viewmodel.RobotViewModel
import com.hydraumc.control.ui.theme.metallicIndustrial

/**
 * Composable that displays the Telemetry screen: a terminal-like log view,
 * plus a new "Ecosystem" tab showing the server's own real V0
 * ecosystem-status scan (GET /api/ecosystem/status - see server.ts's own
 * getEcosystemStatus() and this file's own EcosystemTab for what that
 * actually is). Server owns this work per the ecosystem's own standing
 * "server carries the weight, clients stay thin frontends" principle - this
 * screen only ever displays what the server already scanned, it never
 * touches other repos' files itself.
 * @param viewModel The shared RobotViewModel containing the telemetry logs.
 */
@Composable
fun TelemetryScreen(viewModel: RobotViewModel) {
    var selectedTabIndex by remember { mutableIntStateOf(0) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        TabRow(selectedTabIndex = selectedTabIndex) {
            Tab(
                selected = selectedTabIndex == 0,
                onClick = { selectedTabIndex = 0 },
                text = { Text(stringResource(R.string.tab_telemetry)) },
                icon = { Icon(Icons.Default.Terminal, contentDescription = null) },
            )
            Tab(
                selected = selectedTabIndex == 1,
                onClick = { selectedTabIndex = 1 },
                text = { Text(stringResource(R.string.tab_ecosystem)) },
                icon = { Icon(Icons.Default.Hub, contentDescription = null) },
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        when (selectedTabIndex) {
            0 -> LogsTab(viewModel)
            1 -> EcosystemTab(viewModel)
        }
    }
}

@Composable
private fun LogsTab(viewModel: RobotViewModel) {
    val logs = viewModel.telemetryLogs.value

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.telemetry_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            IconButton(onClick = { viewModel.clearLogs() }) {
                Icon(
                    imageVector = Icons.Default.DeleteSweep,
                    contentDescription = stringResource(R.string.clear_logs_desc),
                    tint = Color.Red,
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Box(
            modifier = Modifier
                .fillMaxSize()
                .metallicIndustrial(backgroundColor = Color.Black)
                .padding(8.dp),
        ) {
            if (logs.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.no_telemetry_data),
                        color = Color.DarkGray,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    reverseLayout = false // Newer logs at the top
                ) {
                    items(logs) { log ->
                        Text(
                            text = log,
                            color = if (log.contains("Error", ignoreCase = true)) Color.Red else Color(0xFF00FF41), // Matrix Green
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Real ecosystem-status display - see server.ts's own getEcosystemStatus()
 * for exactly what this is: sibling repos' own project manifests on the SAME
 * machine the server runs from, PLUS a real per-service live probe (the
 * green/red [LiveStatusDot] below) for whichever of them opt in with a
 * `service` object in their manifest - most of the ecosystem is a library/
 * CLI/firmware/UI that never runs as a network service and correctly shows
 * no bulb at all, not a red one. Refreshes once on first entering this tab,
 * and on demand via the refresh button - not polled continuously. The
 * manifest half of this genuinely doesn't change second to second; the
 * live-probe half honestly can (a service can go up/down between visits)
 * but this tab is a diagnostic check, not a dashboard that needs to be
 * always current - the same manual-refresh pattern as the rest of this
 * tab, kept simple rather than adding a background poll loop for it.
 */
@Composable
private fun EcosystemTab(viewModel: RobotViewModel) {
    val projects = viewModel.ecosystemProjects.value
    val available = viewModel.ecosystemAvailable.value
    val isLoading = viewModel.isLoadingEcosystemStatus.value

    LaunchedEffect(Unit) {
        viewModel.fetchEcosystemStatus()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.tab_ecosystem),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            IconButton(onClick = { viewModel.fetchEcosystemStatus() }) {
                Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.tab_ecosystem))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Box(modifier = Modifier.fillMaxSize()) {
            when {
                isLoading && projects.isEmpty() -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                !available -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.ecosystem_unavailable),
                        color = Color.DarkGray,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 24.dp),
                    )
                }
                else -> {
                    // Grouped by `family` - the same grouping the manifests'
                    // own `family` field already encodes (e.g. "Core Backend
                    // & Clients", "Cognitive AI Node") rather than one flat
                    // 40+ item list.
                    val grouped = projects.groupBy { it.family ?: "—" }
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        grouped.forEach { (family, familyProjects) ->
                            item {
                                Text(
                                    text = family,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Black,
                                    color = Color.Gray,
                                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                                )
                            }
                            items(familyProjects) { project ->
                                EcosystemProjectCard(project)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EcosystemProjectCard(project: EcosystemProject) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .metallicIndustrial()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(project.name, style = MaterialTheme.typography.titleSmall)
                    LiveStatusDot(project)
                }
                Text(
                    listOfNotNull(project.role, project.stack, project.version?.let { "v$it" })
                        .joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                )
            }
            MaturityBadge(project.maturity)
        }
    }
}

/**
 * The real "green bulb / red bulb" per service - not derived from
 * [EcosystemProject.maturity] (a static manifest claim), only from
 * [EcosystemProject.live] (a real, just-now TCP/HTTP probe result the
 * server actually ran - see getEcosystemStatus()'s own probeService()).
 * Renders nothing at all for a project whose manifest never declared a
 * `service` (servicePort null) - most of the ecosystem is a library/CLI/
 * firmware/UI that was never meant to have one, and a bulb on those would
 * misleadingly suggest "down" for something that was never "up" to begin
 * with.
 */
@Composable
private fun LiveStatusDot(project: EcosystemProject) {
    val port = project.servicePort ?: return
    val dotColor = if (project.live == true) Color(0xFF10B981) else Color(0xFFEF4444)
    Spacer(modifier = Modifier.width(6.dp))
    Box(
        modifier = Modifier
            .size(8.dp)
            .background(dotColor, CircleShape)
    )
    Spacer(modifier = Modifier.width(4.dp))
    Text(":$port", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
}

@Composable
private fun MaturityBadge(maturity: String?) {
    val (label, color) = when (maturity) {
        "established" -> stringResource(R.string.maturity_established) to Color(0xFF10B981)
        "functional" -> stringResource(R.string.maturity_functional) to Color(0xFFF59E0B)
        else -> (maturity ?: "—") to Color.Gray
    }
    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.15f), MaterialTheme.shapes.small)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Bold)
    }
}
