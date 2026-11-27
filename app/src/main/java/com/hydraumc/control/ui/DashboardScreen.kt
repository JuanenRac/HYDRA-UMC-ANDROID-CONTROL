// =============================================================================
// HYDRA-UMC CONTROL - The home screen and the health of the server
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.ui

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
import com.hydraumc.control.network.WsStatus
import com.hydraumc.control.ui.theme.HydraColors
import com.hydraumc.control.ui.theme.IconBadge
import com.hydraumc.control.ui.theme.Panel
import com.hydraumc.control.ui.theme.ScreenTitle
import com.hydraumc.control.ui.theme.StatTile
import com.hydraumc.control.viewmodel.HomeState
import com.hydraumc.control.viewmodel.RobotState
import com.hydraumc.control.viewmodel.RobotViewModel
import com.hydraumc.control.viewmodel.SetupStep
import com.hydraumc.control.viewmodel.SetupSteps
import com.hydraumc.control.viewmodel.TotpStep
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/**
 * The home screen: one line that says how the cell is, two figures, the next steps of the installation and the robots in
 * a list. Touching a robot opens its details and the way to Control.
 */
@Composable
fun DashboardScreen(viewModel: RobotViewModel, onOpenControl: () -> Unit = {}, onOpenSettings: () -> Unit = {}) {
    val robots = viewModel.robots.value
    val connection = viewModel.connectionState.value
    val connected = connection == WsStatus.CONNECTED
    val state = HomeState.of(connection, robots)
    val totp by viewModel.totp.state.collectAsState()
    var openRobot by rememberSaveable { mutableStateOf<Int?>(null) }
    // The second factor is read once; the screen of Settings reads it again when it opens.
    LaunchedEffect(connected) { if (connected && viewModel.totp.state.value.step == TotpStep.LOADING) viewModel.totp.load() }

    val time = viewModel.stateAtMs.value?.let { DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(it)) }
    val subtitle = when {
        time == null -> stringResource(R.string.home_waiting)
        viewModel.isShowingStaleData -> stringResource(R.string.home_saved, time)
        else -> stringResource(R.string.home_updated, time)
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp).padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ScreenTitle(Icons.Filled.Home, stringResource(R.string.dashboard_title), subtitle)
        StatusCard(state)

        if (state == HomeState.NotConnected) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { viewModel.connect() }, modifier = Modifier.weight(1f).height(48.dp), shape = BigButtonShape) {
                    Text(stringResource(R.string.common_retry))
                }
                FilledTonalButton(onClick = onOpenSettings, modifier = Modifier.weight(1f).height(48.dp), shape = BigButtonShape) {
                    Text(stringResource(R.string.home_choose_server))
                }
            }
            Panel(Modifier.fillMaxWidth(), tint = HydraColors.Alert) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.home_stop_title), fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.home_stop_text), style = MaterialTheme.typography.bodySmall, color = HydraColors.Muted)
                }
            }
        }

        viewModel.canOtaProgress.value?.let { CanOtaProgressCard(it) }

        if (robots.isNotEmpty()) {
            val online = robots.count { it.online }
            val moving = robots.count { HomeState.moving(it) }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile(
                    Icons.Filled.PrecisionManufacturing, "$online/${robots.size}", stringResource(R.string.home_robots_online),
                    if (online < robots.size) HydraColors.Amber else HydraColors.Ok, Modifier.weight(1f), onOpenControl,
                )
                StatTile(
                    Icons.Filled.PlayCircle, moving.toString(), stringResource(R.string.home_robots_moving_label),
                    HydraColors.Accent, Modifier.weight(1f), onOpenControl,
                )
            }
        }

        if (connected) {
            val server = viewModel.activeServer.value
            val steps = SetupSteps.of(
                server = true,
                controller = server?.let { it.controllerCount > 0 },
                robot = if (robots.isNotEmpty()) true else server?.let { it.robotCount > 0 },
                secondFactor = when (totp.step) {
                    TotpStep.ON, TotpStep.RECOVERY -> true
                    TotpStep.OFF, TotpStep.ENROLLING -> false
                    else -> null
                },
            )
            if (steps.pending.isNotEmpty()) NextSteps(steps, onOpenSettings)
        }

        if (robots.isEmpty()) {
            Text(stringResource(R.string.no_robots), style = MaterialTheme.typography.bodyMedium, color = HydraColors.Muted)
        } else {
            robots.forEach { robot ->
                RobotRow(
                    robot,
                    open = openRobot == robot.id,
                    onToggle = { openRobot = if (openRobot == robot.id) null else robot.id },
                    onControl = {
                        viewModel.selectedRobotId.value = robot.id
                        onOpenControl()
                    },
                )
            }
        }
    }
}

@Composable
private fun StatusCard(state: HomeState) {
    val icon: ImageVector
    val tint: Color
    val title: String
    val hint: String?
    when (state) {
        HomeState.Connecting -> { icon = Icons.Filled.Sync; tint = HydraColors.Amber; title = stringResource(R.string.common_connecting); hint = null }
        HomeState.NotConnected -> { icon = Icons.Filled.CloudOff; tint = HydraColors.Amber; title = stringResource(R.string.status_disconnected); hint = null }
        HomeState.NoRobots -> { icon = Icons.Filled.Info; tint = HydraColors.Accent; title = stringResource(R.string.status_connected); hint = null }
        is HomeState.SafeMode -> {
            icon = Icons.Filled.Warning; tint = HydraColors.Alert
            title = stringResource(R.string.home_safe_mode, state.count); hint = stringResource(R.string.home_safe_mode_hint)
        }
        is HomeState.Offline -> {
            icon = Icons.Filled.CloudOff; tint = HydraColors.Amber
            title = stringResource(R.string.home_offline_robots, state.count); hint = stringResource(R.string.home_offline_robots_hint)
        }
        is HomeState.Moving -> {
            icon = Icons.Filled.PlayCircle; tint = HydraColors.Accent
            title = stringResource(R.string.home_moving, state.count); hint = stringResource(R.string.home_moving_hint)
        }
        is HomeState.Ready -> {
            icon = Icons.Filled.Check; tint = HydraColors.Ok
            title = stringResource(R.string.home_ready); hint = stringResource(R.string.home_ready_hint, state.robots)
        }
    }
    Panel(Modifier.fillMaxWidth(), tint = tint) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            IconBadge(icon, tint, size = 60.dp)
            Column {
                Text(title, style = MaterialTheme.typography.headlineSmall)
                if (hint != null) Text(hint, style = MaterialTheme.typography.bodySmall, color = HydraColors.Muted)
            }
        }
    }
}

@Composable
private fun NextSteps(steps: SetupSteps, onOpenSettings: () -> Unit) {
    Panel(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.home_next_steps), fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.home_steps_done, steps.done, steps.known), style = MaterialTheme.typography.bodySmall, color = HydraColors.Muted)
            }
            steps.pending.forEach { step ->
                val (icon, title, hint) = when (step) {
                    SetupStep.SERVER -> Triple(Icons.Filled.Dns, R.string.home_step_server, R.string.home_step_studio_hint)
                    SetupStep.CONTROLLER -> Triple(Icons.Filled.Memory, R.string.home_step_controller, R.string.home_step_studio_hint)
                    SetupStep.ROBOT -> Triple(Icons.Filled.PrecisionManufacturing, R.string.home_step_robot, R.string.home_step_studio_hint)
                    SetupStep.SECOND_FACTOR -> Triple(Icons.Filled.Shield, R.string.home_step_second_factor, R.string.home_step_second_factor_hint)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    IconBadge(icon, size = 40.dp)
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(title), fontWeight = FontWeight.Medium)
                        Text(stringResource(hint), style = MaterialTheme.typography.bodySmall, color = HydraColors.Muted)
                    }
                    // Only the second factor can be done from the phone.
                    if (step == SetupStep.SECOND_FACTOR) TextButton(onClick = onOpenSettings) { Text(stringResource(R.string.home_step_do), color = HydraColors.Accent) }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RobotRow(robot: RobotState, open: Boolean, onToggle: () -> Unit, onControl: () -> Unit) {
    val attention = !robot.online || !robot.isEnabled
    val tint = if (!robot.isEnabled) HydraColors.Alert else HydraColors.Amber
    val line = when {
        !robot.isEnabled -> stringResource(R.string.watch_alert_robot_safe_mode, robot.name)
        !robot.online -> stringResource(R.string.common_offline)
        robot.isPlaying && !robot.isPaused -> stringResource(R.string.playing_status)
        else -> stringResource(R.string.home_robot_online_line, formatCoord(robot.posX), formatCoord(robot.posY), formatCoord(robot.posZ))
    }
    Panel(Modifier.fillMaxWidth(), tint = if (attention) tint else null, onClick = onToggle) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconBadge(Icons.Filled.PrecisionManufacturing, if (attention) tint else HydraColors.Ok, size = 40.dp)
                Column(Modifier.weight(1f)) {
                    Text(if (robot.role.isBlank()) robot.name else "${robot.name} · ${robot.role}", fontWeight = FontWeight.SemiBold)
                    Text(line, style = MaterialTheme.typography.bodySmall, color = HydraColors.Muted)
                }
                Icon(if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null, tint = HydraColors.Muted)
            }
            if (open) {
                Text("${robot.manufacturer} | ${robot.model}", style = MaterialTheme.typography.bodySmall, color = HydraColors.Accent)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ModuleIndicator(label = "CAM", active = robot.hasCamera)
                    ModuleIndicator(label = "XY", active = robot.hasXYTable)
                    ModuleIndicator(label = "ATC", active = robot.hasAtc)
                    ModuleIndicator(label = "PNP", active = robot.hasPnP)
                    ModuleIndicator(label = "CNC", active = robot.hasCNC)
                    ModuleIndicator(label = "LSR", active = robot.hasLaser)
                    ModuleIndicator(label = "BED", active = robot.hasHeatedBed)
                    ModuleIndicator(label = "VAC", active = robot.hasVacuumTable)
                    ModuleIndicator(label = "RCK", active = robot.hasRack)
                }
                Text(stringResource(R.string.tool, robot.currentTool), style = MaterialTheme.typography.bodySmall)
                if (robot.hasXYTable) {
                    Text(stringResource(R.string.pos_xy) + " X ${formatCoord(robot.xyPosX)} · Y ${formatCoord(robot.xyPosY)}", style = MaterialTheme.typography.bodySmall)
                }
                Text(
                    stringResource(R.string.speed, robot.speed.toInt().toString()) + " · " + stringResource(R.string.accel, robot.acceleration.toInt().toString()),
                    style = MaterialTheme.typography.bodySmall,
                )
                FilledTonalButton(onClick = onControl, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.home_robot_open_control)) }
            }
        }
    }
}

/** The health of the server in use: host, uptime, counts and load. Shown on the telemetry screen. */
@Composable
fun SystemHealthCard(viewModel: RobotViewModel) {
    val server = viewModel.activeServer.value ?: return
    if (viewModel.connectionState.value != WsStatus.CONNECTED) return
    val metrics = viewModel.metrics.value
    Panel(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.system_health), fontWeight = FontWeight.SemiBold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(stringResource(R.string.host_label, server.hostname), style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(R.string.uptime_label, formatUptime(server.uptimeSeconds)), style = MaterialTheme.typography.bodySmall)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(stringResource(R.string.controllers_label, server.controllerCount), style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(R.string.robots_label, server.robotCount), style = MaterialTheme.typography.bodySmall)
                }
            }
            if (metrics != null) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    MetricItem(label = "CPU", value = "${metrics.cpuLoad}%", color = if (metrics.cpuLoad > 80) HydraColors.Alert else HydraColors.Ok)
                    MetricItem(label = "MEM", value = "${metrics.memoryUsage}%", color = if (metrics.memoryUsage > 80) HydraColors.Alert else HydraColors.Ok)
                    MetricItem(label = "TEMP", value = "${metrics.temp.toInt()}°C", color = if (metrics.temp > 70) HydraColors.Alert else HydraColors.Ok)
                }
            }
        }
    }
}

/** 
 * Helper function to format coordinate values to two decimal places.
 * @param value The double value to format.
 * @return Formatted string.
 */
fun formatCoord(value: Double): String {
    return String.format(Locale.US, "%.2f", value)
}

/**
 * Small metric indicator for the system health panel.
 */
@Composable
fun MetricItem(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        Text(text = value, style = MaterialTheme.typography.bodySmall, color = color, fontWeight = FontWeight.Bold)
    }
}

/**
 * Formats seconds into a human-readable uptime string.
 * @param seconds Total uptime in seconds.
 * @return Formatted uptime (e.g., "2d 4h 15m").
 */
fun formatUptime(seconds: Int): String {
    val d = seconds / 86400
    val h = (seconds % 86400) / 3600
    val m = (seconds % 3600) / 60
    return if (d > 0) "${d}d ${h}h ${m}m" else if (h > 0) "${h}h ${m}m" else "${m}m"
}

/**
 * Small indicator for active/inactive modules.
 */
@Composable
fun ModuleIndicator(label: String, active: Boolean) {
    Surface(
        color = if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color.Transparent,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp, 
            if (active) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.5f)
        )
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = if (active) MaterialTheme.colorScheme.primary else Color.Gray,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal
        )
    }
}
