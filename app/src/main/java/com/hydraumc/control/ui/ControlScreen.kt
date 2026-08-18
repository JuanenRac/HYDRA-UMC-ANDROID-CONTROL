// =============================================================================
// HYDRA-UMC CONTROL - Advanced Manual Robot Control Console
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0 - see LICENSE
// =============================================================================
package com.hydraumc.control.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*

import android.os.Vibrator
import android.os.VibrationEffect
import android.content.Context
import android.annotation.SuppressLint

import com.hydraumc.control.viewmodel.RobotViewModel
import com.hydraumc.control.viewmodel.RobotState
import com.hydraumc.control.R
import com.hydraumc.control.ui.theme.metallicIndustrial
import com.hydraumc.control.ui.theme.StatusLed
import com.hydraumc.control.ui.theme.HydraButton
import com.hydraumc.control.ui.theme.IndustrialDanger
import com.hydraumc.control.ui.theme.MetallicCyan

/**
 * Advanced Digital Readout (DRO) Component
 */
@Composable
fun DigitalReadout(label: String, value: Double, unit: String = "", color: Color = MetallicCyan, decimals: Int = 2) {
    Column(
        modifier = Modifier
            .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
            .padding(8.dp)
            .width(80.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.Gray, fontWeight = FontWeight.Bold)
        Text(
            text = java.util.Locale.US.let { String.format(it, "%." + decimals + "f", value) },
            style = MaterialTheme.typography.titleMedium.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
            color = color,
            fontWeight = FontWeight.Black,
            maxLines = 1
        )
        if (unit.isNotEmpty()) {
            Text(unit, style = MaterialTheme.typography.labelSmall, color = Color.DarkGray)
        }
    }
}

/**
 * Endstop Indicator Component
 */
@Composable
fun EndstopIndicator(label: String, active: Boolean) {
    val glowColor by animateColorAsState(if (active) Color.Red else Color(0xFF1A1A1A))
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(glowColor)
        )
        Text(label, style = MaterialTheme.typography.labelSmall, color = if (active) Color.Red else Color.Gray, fontWeight = FontWeight.Black, fontSize = 9.sp)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ControlScreen(viewModel: RobotViewModel) {
    val context = LocalContext.current
    val vibrator = remember { context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator }
    
    @SuppressLint("MissingPermission")
    fun vibrate(pattern: LongArray? = null, duration: Long = 50) {
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                if (pattern != null) vibrator?.vibrate(VibrationEffect.createWaveform(pattern, -1))
                else vibrator?.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                if (pattern != null) vibrator?.vibrate(pattern, -1)
                else vibrator?.vibrate(duration)
            }
        } catch (_: Exception) {}
    }

    val robots = viewModel.robots.value
    val selectedId = viewModel.selectedRobotId.value
    val connectionStatus = viewModel.connectionStatus.value
    val selectedRobot = robots.find { it.id == selectedId }

    var expandedRobot by remember { mutableStateOf(false) }
    var stepSize by remember { mutableDoubleStateOf(10.0) }
    var activeTarget by remember { mutableStateOf("robot") }
    
    var speedState by remember(selectedRobot?.speed) { mutableFloatStateOf(selectedRobot?.speed?.toFloat() ?: 100f) }
    var accelState by remember(selectedRobot?.acceleration) { mutableFloatStateOf(selectedRobot?.acceleration?.toFloat() ?: 500f) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .padding(bottom = 110.dp) // Space for playback bar
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Top Status Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(stringResource(R.string.robot_control_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                    Row {
                        Text(
                            text = "STATUS: ",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.LightGray,
                            fontWeight = FontWeight.Bold
                        )
                        val isConnected = connectionStatus.contains("Connected", ignoreCase = true)
                        Text(
                            text = (if (isConnected) stringResource(R.string.status_connected) else connectionStatus).uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isConnected) Color(0xFF10B981) else Color(0xFFF43F5E),
                            fontWeight = FontWeight.Black
                        )
                    }
                }
                StatusLed(
                    isOn = connectionStatus.contains("Connected", ignoreCase = true),
                    activeColor = MetallicCyan,
                    size = 20.dp
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Robot Selector Card
            Box(modifier = Modifier.fillMaxWidth().metallicIndustrial(backgroundColor = Color(0xFF111827))) {
                ExposedDropdownMenuBox(
                    expanded = expandedRobot,
                    onExpandedChange = { expandedRobot = !expandedRobot },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    OutlinedTextField(
                        value = selectedRobot?.name ?: "SELECT ACTIVE NODE",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("NETWORKED ROBOT") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedRobot) },
                        modifier = Modifier.menuAnchor(type = MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                        colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                        textStyle = LocalTextStyle.current.copy(fontWeight = FontWeight.Black, color = Color.White)
                    )
                    ExposedDropdownMenu(expanded = expandedRobot, onDismissRequest = { expandedRobot = false }) {
                        robots.forEach { robot ->
                            DropdownMenuItem(
                                text = { Text("${robot.name} (A${robot.id} - ${robot.model})") },
                                onClick = { viewModel.selectedRobotId.value = robot.id; expandedRobot = false }
                            )
                        }
                    }
                }
            }

            if (selectedRobot != null) {
                Spacer(modifier = Modifier.height(12.dp))

                // TELEMETRY DRO SECTION
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.real_time_telemetry), style = MaterialTheme.typography.labelSmall, color = Color.Gray, fontWeight = FontWeight.Black)
                            if (selectedRobot.isPlaying) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(Color.Green))
                                    Text("${stringResource(R.string.playing_status).uppercase()} - STEP ${selectedRobot.activeStep}", style = MaterialTheme.typography.labelSmall, color = Color.Green, fontWeight = FontWeight.Black)
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // Cartesian DRO
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            DigitalReadout("X", selectedRobot.posX, "mm")
                            DigitalReadout("Y", selectedRobot.posY, "mm")
                            DigitalReadout("Z", selectedRobot.posZ, "mm")
                            DigitalReadout("SPD", selectedRobot.speed, "%", Color.Yellow, decimals = 0)
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // Joint DRO 1
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            DigitalReadout("J1", selectedRobot.j1, "°", Color(0xFF0EA5E9))
                            DigitalReadout("J2", selectedRobot.j2, "°", Color(0xFF0EA5E9))
                            DigitalReadout("J3", selectedRobot.j3, "°", Color(0xFF0EA5E9))
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Joint DRO 2
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            DigitalReadout("J4", selectedRobot.j4, "°", Color(0xFF0EA5E9))
                            DigitalReadout("J5", selectedRobot.j5, "°", Color(0xFF0EA5E9))
                            DigitalReadout("J6", selectedRobot.j6, "°", Color(0xFF0EA5E9))
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // Endstop Matrix
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("x1", "x2", "y1", "y2", "z0").forEach { endstop ->
                                EndstopIndicator(endstop.uppercase(), selectedRobot.endstops[endstop] ?: false)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Main Enable/Disable
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    HydraButton(
                        text = if (selectedRobot.online) stringResource(R.string.disable_node) else stringResource(R.string.enable_node),
                        icon = if (selectedRobot.online) Icons.Default.Block else Icons.Default.CheckCircle,
                        onClick = { viewModel.sendCommand(if (selectedRobot.online) "disable" else "enable") },
                        backgroundColor = if (selectedRobot.online) IndustrialDanger else Color(0xFF15803D),
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // JOYSTICK
                Box(modifier = Modifier.fillMaxWidth().metallicIndustrial()) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(8.dp)) {
                        Text(stringResource(R.string.virtual_joystick).uppercase(), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = Color.Gray)
                        
                        if (selectedRobot.hasXYTable) {
                            Row(modifier = Modifier.padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(selected = activeTarget == "robot", onClick = { activeTarget = "robot" }, label = { Text(stringResource(R.string.arm_label)) })
                                FilterChip(selected = activeTarget == "xytable", onClick = { activeTarget = "xytable" }, label = { Text(stringResource(R.string.xy_table).uppercase()) })
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(1.0, 10.0, 50.0).forEach { size ->
                                FilterChip(selected = stepSize == size, onClick = { stepSize = size }, label = { Text("${size.toInt()}mm") })
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        val btnSize = 85.dp
                        val jogColor = Color(0xFF0369A1)
                        
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            // XY Pad
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                HydraButton(text = "Y+", onClick = { vibrate(); viewModel.jog(activeTarget, "y", stepSize) }, enabled = selectedRobot.online, modifier = Modifier.size(btnSize), backgroundColor = jogColor)
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                                    HydraButton(text = "X-", onClick = { vibrate(); viewModel.jog(activeTarget, "x", -stepSize) }, enabled = selectedRobot.online, modifier = Modifier.size(btnSize), backgroundColor = jogColor)
                                    HydraButton(text = "X+", onClick = { vibrate(); viewModel.jog(activeTarget, "x", stepSize) }, enabled = selectedRobot.online, modifier = Modifier.size(btnSize), backgroundColor = jogColor)
                                }
                                HydraButton(text = "Y-", onClick = { vibrate(); viewModel.jog(activeTarget, "y", -stepSize) }, enabled = selectedRobot.online, modifier = Modifier.size(btnSize), backgroundColor = jogColor)
                            }
                            
                            // Z Column
                            if (activeTarget == "robot") {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    HydraButton(text = "Z+", icon = Icons.Default.KeyboardArrowUp, onClick = { vibrate(); viewModel.jog(activeTarget, "z", stepSize) }, enabled = selectedRobot.online, modifier = Modifier.size(btnSize), backgroundColor = Color(0xFF0891B2))
                                    Spacer(modifier = Modifier.height(12.dp))
                                    HydraButton(text = "Z-", icon = Icons.Default.KeyboardArrowDown, onClick = { vibrate(); viewModel.jog(activeTarget, "z", -stepSize) }, enabled = selectedRobot.online, modifier = Modifier.size(btnSize), backgroundColor = Color(0xFF0891B2))
                                }
                            }
                        }
                    }
                }

                // I/O SECTION
                Spacer(modifier = Modifier.height(16.dp))
                Text(stringResource(R.string.industrial_io_hub), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = Color.Gray)
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    HydraButton(
                        text = "VALVE 1",
                        icon = Icons.Default.WaterDrop,
                        onClick = { vibrate(); viewModel.toggleValve(0) },
                        modifier = Modifier.weight(1f),
                        backgroundColor = if (selectedRobot.valves.getOrElse(0){false}) Color(0xFF00ACC1) else Color(0xFF1E293B)
                    )
                    HydraButton(
                        text = "VALVE 2",
                        icon = Icons.Default.WaterDrop,
                        onClick = { vibrate(); viewModel.toggleValve(1) },
                        modifier = Modifier.weight(1f),
                        backgroundColor = if (selectedRobot.valves.getOrElse(1){false}) Color(0xFF00ACC1) else Color(0xFF1E293B)
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    HydraButton(
                        text = "VACUUM 1",
                        icon = Icons.Default.PowerSettingsNew,
                        onClick = { vibrate(); viewModel.togglePump(0) },
                        modifier = Modifier.weight(1f),
                        backgroundColor = if (selectedRobot.pumps.getOrElse(0){false}) Color(0xFFFB8C00) else Color(0xFF1E293B)
                    )
                    HydraButton(
                        text = "VACUUM 2",
                        icon = Icons.Default.PowerSettingsNew,
                        onClick = { vibrate(); viewModel.togglePump(1) },
                        modifier = Modifier.weight(1f),
                        backgroundColor = if (selectedRobot.pumps.getOrElse(1){false}) Color(0xFFFB8C00) else Color(0xFF1E293B)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                Spacer(modifier = Modifier.height(16.dp))

                // SETTINGS SLIDERS
                Text(stringResource(R.string.tab_settings).uppercase(), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = Color.Gray)
                Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(stringResource(R.string.feedrate_override), style = MaterialTheme.typography.bodySmall, color = Color.White)
                        Text("${speedState.toInt()}%", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = MetallicCyan)
                    }
                    Slider(
                        value = speedState,
                        onValueChange = { speedState = it },
                        onValueChangeFinished = { viewModel.setSpeed(speedState.toDouble(), accelState.toDouble()) },
                        valueRange = 10f..500f,
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(stringResource(R.string.acceleration_ramp), style = MaterialTheme.typography.bodySmall, color = Color.White)
                        Text("${accelState.toInt()}%", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = Color.Yellow)
                    }
                    Slider(
                        value = accelState,
                        onValueChange = { accelState = it },
                        onValueChangeFinished = { viewModel.setSpeed(speedState.toDouble(), accelState.toDouble()) },
                        valueRange = 100f..2000f,
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // OPERATION PROGRESS BAR
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.operation_progress), style = MaterialTheme.typography.labelSmall, color = Color.Gray, fontWeight = FontWeight.Black)
                            val progress = if (selectedRobot.recordedPointsCount > 0) (selectedRobot.activeStep.coerceAtLeast(0).toFloat() / (selectedRobot.recordedPointsCount - 1).coerceAtLeast(1).toFloat()) else 0f
                            Text(String.format(java.util.Locale.US, "%.1f%%", progress * 100), style = MaterialTheme.typography.labelSmall, color = Color.Green, fontWeight = FontWeight.Black)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        val progress = if (selectedRobot.recordedPointsCount > 0) (selectedRobot.activeStep.coerceAtLeast(0).toFloat() / (selectedRobot.recordedPointsCount - 1).coerceAtLeast(1).toFloat()) else 0f
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                            color = Color(0xFF10B981),
                            trackColor = Color.DarkGray
                        )
                    }
                }
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.LinkOff, contentDescription = null, modifier = Modifier.size(48.dp), tint = Color.DarkGray)
                        Text(stringResource(R.string.no_node_selected), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = Color.DarkGray)
                        Text(stringResource(R.string.select_node_desc), style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                    }
                }
            }
        }

        // FLOATING PLAYBACK CONSOLE
        if (selectedRobot != null) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(12.dp)
                    .height(90.dp),
                color = Color(0xFF1E293B).copy(alpha = 0.95f),
                shape = RoundedCornerShape(24.dp),
                shadowElevation = 12.dp,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
            ) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // EMERGENCY STOP
                    IconButton(
                        onClick = { 
                            vibrate(longArrayOf(0, 150, 50, 150, 50, 150))
                            viewModel.sendCommand("stop") 
                        },
                        modifier = Modifier.size(64.dp).background(Color.Red.copy(alpha = 0.15f), CircleShape).border(2.dp, Color.Red, CircleShape)
                    ) {
                        Icon(Icons.Default.Dangerous, contentDescription = "E-STOP", tint = Color.Red, modifier = Modifier.size(36.dp))
                    }

                    // PLAY
                    IconButton(
                        onClick = { vibrate(); viewModel.sendCommand("play") },
                        enabled = selectedRobot.online,
                        modifier = Modifier.size(54.dp).background(if(selectedRobot.online) Color(0xFF15803D) else Color.DarkGray, CircleShape)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "PLAY", tint = Color.White)
                    }

                    // PAUSE / RESUME
                    IconButton(
                        onClick = { vibrate(); viewModel.sendCommand("pause") },
                        enabled = selectedRobot.online && selectedRobot.isPlaying,
                        modifier = Modifier.size(54.dp).background(if(selectedRobot.online && selectedRobot.isPlaying) Color(0xFFB45309) else Color.DarkGray, CircleShape)
                    ) {
                        Icon(if (selectedRobot.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause, contentDescription = "PAUSE", tint = Color.White)
                    }

                    // STOP
                    IconButton(
                        onClick = { vibrate(); viewModel.sendCommand("stop") },
                        enabled = selectedRobot.online && (selectedRobot.isPlaying || selectedRobot.isPaused),
                        modifier = Modifier.size(54.dp).background(if(selectedRobot.online && (selectedRobot.isPlaying || selectedRobot.isPaused)) Color(0xFF991B1B) else Color.DarkGray, CircleShape)
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = "STOP", tint = Color.White)
                    }
                }
            }
        }
    }
}
