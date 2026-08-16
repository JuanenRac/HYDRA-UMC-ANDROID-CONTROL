package com.hydraumc.control.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hydraumc.control.viewmodel.RobotViewModel
import com.hydraumc.control.R
import kotlin.math.round

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(viewModel: RobotViewModel) {
    val robots = viewModel.robots.value
    val connectionStatus = viewModel.connectionStatus.value

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())
    ) {
        Text(stringResource(R.string.dashboard_title), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.server_status, connectionStatus), style = MaterialTheme.typography.bodyMedium, color = if(connectionStatus == stringResource(R.string.status_connected)) Color(0xFF2E7D32) else Color.Red)
        
        Spacer(modifier = Modifier.height(24.dp))
        
        if (robots.isEmpty()) {
            Text(stringResource(R.string.no_robots), style = MaterialTheme.typography.bodyLarge)
        } else {
            robots.forEach { robot ->
                OutlinedCard(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(robot.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Badge(containerColor = if (robot.online) Color(0xFF2E7D32) else Color.Red) {
                                Text(if (robot.online) stringResource(R.string.online) else stringResource(R.string.offline), color = Color.White)
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Text(stringResource(R.string.tool, robot.currentTool), style = MaterialTheme.typography.bodyMedium)
                        
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
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            Text(stringResource(R.string.playing_status), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}

fun formatCoord(value: Double): String {
    return String.format("%.2f", value)
}
