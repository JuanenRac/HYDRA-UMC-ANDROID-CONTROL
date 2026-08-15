package com.hydraumc.control.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hydraumc.control.viewmodel.RobotViewModel
import kotlin.math.round

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(viewModel: RobotViewModel) {
    val robots = viewModel.robots.value
    val connectionStatus = viewModel.connectionStatus.value

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())
    ) {
        Text("Dashboard (Visión General)", style = MaterialTheme.typography.headlineMedium)
        Text("Estado del Servidor: $connectionStatus", style = MaterialTheme.typography.bodyMedium, color = if(connectionStatus == "Conectado") Color(0xFF2E7D32) else Color.Red)
        
        Spacer(modifier = Modifier.height(24.dp))
        
        if (robots.isEmpty()) {
            Text("No hay robots conectados o configurados.", style = MaterialTheme.typography.bodyLarge)
        } else {
            robots.forEach { robot ->
                OutlinedCard(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(robot.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Badge(containerColor = if (robot.online) Color(0xFF2E7D32) else Color.Red) {
                                Text(if (robot.online) "ONLINE" else "OFFLINE", color = Color.White)
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Text("Herramienta: ${robot.currentTool}", style = MaterialTheme.typography.bodyMedium)
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text("Posición (Brazo):", fontWeight = FontWeight.Bold)
                        Text("X: ${formatCoord(robot.posX)} | Y: ${formatCoord(robot.posY)} | Z: ${formatCoord(robot.posZ)}", style = MaterialTheme.typography.bodyMedium)
                        
                        if (robot.hasXYTable) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Posición (Mesa XY):", fontWeight = FontWeight.Bold)
                            Text("X: ${formatCoord(robot.xyPosX)} | Y: ${formatCoord(robot.xyPosY)}", style = MaterialTheme.typography.bodyMedium)
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Velocidad: ${robot.speed.toInt()} mm/s", style = MaterialTheme.typography.bodySmall)
                            Text("Acel: ${robot.acceleration.toInt()} mm/s²", style = MaterialTheme.typography.bodySmall)
                        }
                        
                        if (robot.isPlaying) {
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            Text("En reproducción...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
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
