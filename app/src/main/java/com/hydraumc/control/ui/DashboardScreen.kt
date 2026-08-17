// =============================================================================
// HYDRA-UMC CONTROL - Main dashboard providing an overview of all robots
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0 - see LICENSE
// =============================================================================
package com.hydraumc.control.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.hydraumc.control.viewmodel.RobotViewModel
import com.hydraumc.control.R
import com.hydraumc.control.ui.theme.metallicIndustrial
import com.hydraumc.control.ui.theme.StatusLed
import java.util.Locale
import kotlin.math.absoluteValue

/**
 * Main dashboard screen displaying a summary of all connected robots in a carousel.
 * 
 * @param viewModel The shared RobotViewModel.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(viewModel: RobotViewModel) {
    /** Current list of robots fetched from the system state. */
    val robots = viewModel.robots.value
    /** Current server connection status. */
    val connectionStatus = viewModel.connectionStatus.value
    /** Current active server info. */
    val activeServer = viewModel.activeServer.value
    /** Boolean flag indicating if the app is successfully connected to a server. */
    val isConnected = connectionStatus == stringResource(R.string.status_connected)

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatusLed(
                isOn = isConnected,
                activeColor = com.hydraumc.control.ui.theme.MetallicCyan,
                size = 16.dp,
            )
            Text(stringResource(R.string.dashboard_title), style = MaterialTheme.typography.headlineMedium)
        }
        
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.server_status, "").trim(),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
            )
            val statusColor = when (connectionStatus) {
                stringResource(R.string.status_connected) -> Color(0xFF00C853)
                stringResource(R.string.status_connecting) -> Color(0xFFFFA000)
                else -> Color.Red
            }
            Text(
                text = " $connectionStatus",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = statusColor,
            )
        }
        
        if (isConnected && (activeServer != null)) {
            Spacer(modifier = Modifier.height(16.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .metallicIndustrial(backgroundColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(12.dp)
            ) {
                Column {
                    Text(
                        text = "SYSTEM HEALTH",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Host: ${activeServer.hostname}", style = MaterialTheme.typography.bodySmall)
                            Text("Uptime: ${formatUptime(activeServer.uptimeSeconds)}", style = MaterialTheme.typography.bodySmall)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Controllers: ${activeServer.controllerCount}", style = MaterialTheme.typography.bodySmall)
                            Text("Robots: ${activeServer.robotCount}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        if (robots.isEmpty()) {
            Text(stringResource(R.string.no_robots), style = MaterialTheme.typography.bodyLarge)
        } else {
            /** State for the horizontal pager (carousel). */
            val pagerState = rememberPagerState { robots.size }
            
            HorizontalPager(
                state = pagerState,
                contentPadding = PaddingValues(horizontal = 32.dp),
                modifier = Modifier.fillMaxWidth().height(450.dp),
            ) { page ->
                /** The specific robot data for this pager item. */
                val robot = robots[page]
                
                Box(
                    modifier = Modifier
                        .graphicsLayer {
                            /** Calculate offset for 3D transition effect. */
                            val pageOffset = (
                                    (pagerState.currentPage - page) + pagerState
                                        .currentPageOffsetFraction
                                    ).absoluteValue
                            
                            // 3D fashion effect: scale and alpha
                            alpha = lerp(
                                start = 0.5f,
                                stop = 1f,
                                fraction = 1f - pageOffset.coerceIn(0f, 1f),
                            )
                            scaleX = lerp(
                                start = 0.8f,
                                stop = 1f,
                                fraction = 1f - pageOffset.coerceIn(0f, 1f),
                            )
                            scaleY = lerp(
                                start = 0.8f,
                                stop = 1f,
                                fraction = 1f - pageOffset.coerceIn(0f, 1f),
                            )
                        }
                        .fillMaxWidth()
                        .padding(8.dp)
                        .metallicIndustrial(),
                ) {
                    Column {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(robot.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                            StatusLed(
                                isOn = robot.online,
                                activeColor = Color(0xFF2E7D32),
                                label = if (robot.online) stringResource(R.string.online) else stringResource(R.string.offline),
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Text(stringResource(R.string.tool, robot.currentTool), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text(stringResource(R.string.pos_arm), fontWeight = FontWeight.Bold)
                        Text("X: ${formatCoord(robot.posX)} | Y: ${formatCoord(robot.posY)} | Z: ${formatCoord(robot.posZ)}", style = MaterialTheme.typography.bodyMedium)
                        
                        if (robot.hasXYTable) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(stringResource(R.string.pos_xy), fontWeight = FontWeight.Bold)
                            Text("X: ${formatCoord(robot.xyPosX)} | Y: ${formatCoord(robot.xyPosY)}", style = MaterialTheme.typography.bodyMedium)
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(stringResource(R.string.speed, robot.speed.toInt().toString()), style = MaterialTheme.typography.bodySmall)
                            Text(stringResource(R.string.accel, robot.acceleration.toInt().toString()), style = MaterialTheme.typography.bodySmall)
                        }
                        
                        if (robot.isPlaying) {
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.tertiary)
                            Text(stringResource(R.string.playing_status), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
                        }
                    }
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
