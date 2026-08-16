// =============================================================================
// HYDRA-UMC CONTROL - Manual robot control screen with joystick and settings
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0 - see LICENSE
// =============================================================================
package com.hydraumc.control.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import android.os.Vibrator
import android.os.VibrationEffect
import android.content.Context
import android.annotation.SuppressLint
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import com.hydraumc.control.viewmodel.RobotViewModel
import com.hydraumc.control.R
import com.hydraumc.control.ui.theme.metallicIndustrial
import com.hydraumc.control.ui.theme.StatusLed
import com.hydraumc.control.ui.theme.HydraButton
import com.hydraumc.control.ui.theme.IndustrialDanger

/**
 * Main control screen providing manual interface for robot movement, speed,
 * tool selection, and playback controls.
 * 
 * @param viewModel The shared RobotViewModel.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ControlScreen(viewModel: RobotViewModel) {
    val context = LocalContext.current
    val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    
    @SuppressLint("MissingPermission")
    fun vibrate() {
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                vibrator.vibrate(50)
            }
        } catch (e: Exception) {
            // Vibrate failed, ignore
        }
    }
    /** List of all robots fetched from the ViewModel. */
    val robots = viewModel.robots.value
    /** ID of the currently selected robot. */
    val selectedId = viewModel.selectedRobotId.value
    /** Current connection status string. */
    val connectionStatus = viewModel.connectionStatus.value
    /** Last reported error message, if any. */
    val lastError = viewModel.lastError.value

    /** State to manage the robot selection dropdown expansion. */
    var expandedRobot by remember { mutableStateOf(value = false) }
    /** State to manage the tool selection dropdown expansion. */
    var expandedTool by remember { mutableStateOf(value = false) }
    /** Currently selected step size for jogging. */
    var stepSize by remember { mutableDoubleStateOf(10.0) }
    /** Target for movement commands (e.g., "robot" or "xytable"). */
    var activeTarget by remember { mutableStateOf("robot") }
    
    /** The RobotView object for the currently selected robot ID. */
    val selectedRobot = robots.find { it.id == selectedId }
    
    /** Local state for speed slider, synced with the robot's current speed. */
    var speedState by remember(selectedRobot?.speed) { mutableFloatStateOf(selectedRobot?.speed?.toFloat() ?: 100f) }
    /** Local state for acceleration slider, synced with the robot's current acceleration. */
    var accelState by remember(selectedRobot?.acceleration) { mutableFloatStateOf(selectedRobot?.acceleration?.toFloat() ?: 500f) }

    /** Rationale: Reset active target to robot if the selected robot doesn't have an XY table. */
    LaunchedEffect(selectedRobot) {
        if (selectedRobot?.hasXYTable == false) {
            activeTarget = "robot"
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .padding(bottom = if (selectedRobot != null) 100.dp else 0.dp) // Leave space for fixed buttons
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatusLed(
                    isOn = connectionStatus == stringResource(R.string.status_connected),
                    activeColor = com.hydraumc.control.ui.theme.MetallicCyan,
                    size = 16.dp,
                )
                Text(stringResource(R.string.control_robots), style = MaterialTheme.typography.headlineMedium)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.status_label),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White
                )
                val statusColor = when (connectionStatus) {
                    stringResource(R.string.status_connected) -> Color(0xFF00C853)
                    stringResource(R.string.status_connecting) -> Color(0xFFFFA000)
                    else -> Color.Red
                }
                Text(
                    text = connectionStatus,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = statusColor,
                )
            }
            
            lastError?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }

            Spacer(modifier = Modifier.height(16.dp))
            
            // EMERGENCY STOP BUTTON
            if (selectedRobot != null) {
                HydraButton(
                    text = stringResource(R.string.e_stop),
                    onClick = { 
                        vibrate()
                        viewModel.sendCommand("stop")
                    },
                    backgroundColor = Color.Red,
                    modifier = Modifier.fillMaxWidth().height(64.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
            
            // Robot Selection
            ExposedDropdownMenuBox(
                expanded = expandedRobot,
                onExpandedChange = { expandedRobot = !expandedRobot },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = selectedRobot?.name ?: stringResource(R.string.select_robot),
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedRobot) },
                    modifier = Modifier.menuAnchor(type = MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
                )
                ExposedDropdownMenu(
                    expanded = expandedRobot,
                    onDismissRequest = { expandedRobot = false }
                ) {
                    robots.forEach { robot ->
                        DropdownMenuItem(
                            text = { Text("${robot.name} (ID: ${robot.id})") },
                            onClick = {
                                viewModel.selectedRobotId.value = robot.id
                                expandedRobot = false
                            }
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            if (selectedRobot != null) {
                Box(modifier = Modifier.fillMaxWidth().metallicIndustrial()) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            HydraButton(
                                text = stringResource(R.string.enable),
                                onClick = { viewModel.sendCommand("enable") },
                                enabled = !selectedRobot.online,
                                backgroundColor = Color(0xFF2E7D32),
                                modifier = Modifier.weight(1f)
                            )
                            HydraButton(
                                text = stringResource(R.string.disable),
                                onClick = { viewModel.sendCommand("disable") },
                                enabled = selectedRobot.online,
                                backgroundColor = IndustrialDanger,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(16.dp))
                
                // Joystick Section
                Box(modifier = Modifier.fillMaxWidth().metallicIndustrial()) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.virtual_joystick), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        if (selectedRobot.hasXYTable) {
                            Row(modifier = Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(selected = activeTarget == "robot", onClick = { activeTarget = "robot" }, label = { Text(stringResource(R.string.arm)) })
                                FilterChip(selected = activeTarget == "xytable", onClick = { activeTarget = "xytable" }, label = { Text(stringResource(R.string.xy_table)) })
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(R.string.step_label))
                            listOf(1.0, 10.0, 50.0).forEach { size ->
                                FilterChip(selected = stepSize == size, onClick = { stepSize = size }, label = { Text("${size.toInt()}mm") })
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // Movement Buttons (50% larger: base size ~60dp -> 90dp)
                        val btnSize = 90.dp
                        Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                HydraButton(text = "Y+", onClick = { vibrate(); viewModel.jog(activeTarget, "y", stepSize) }, enabled = selectedRobot.online, modifier = Modifier.size(btnSize))
                                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                                    HydraButton(text = "X-", onClick = { vibrate(); viewModel.jog(activeTarget, "x", -stepSize) }, enabled = selectedRobot.online, modifier = Modifier.size(btnSize))
                                    HydraButton(text = "X+", onClick = { vibrate(); viewModel.jog(activeTarget, "x", stepSize) }, enabled = selectedRobot.online, modifier = Modifier.size(btnSize))
                                }
                                HydraButton(text = "Y-", onClick = { vibrate(); viewModel.jog(activeTarget, "y", -stepSize) }, enabled = selectedRobot.online, modifier = Modifier.size(btnSize))
                            }
                            if (activeTarget == "robot") {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    HydraButton(text = "Z+", onClick = { vibrate(); viewModel.jog(activeTarget, "z", stepSize) }, enabled = selectedRobot.online, modifier = Modifier.size(btnSize))
                                    Spacer(modifier = Modifier.height(24.dp))
                                    HydraButton(text = "Z-", onClick = { vibrate(); viewModel.jog(activeTarget, "z", -stepSize) }, enabled = selectedRobot.online, modifier = Modifier.size(btnSize))
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(16.dp))
                
                // Kinematics / Settings
                Box(modifier = Modifier.fillMaxWidth().metallicIndustrial()) {
                    Column {
                        Text(stringResource(R.string.kinematics), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(stringResource(R.string.speed, speedState.toInt().toString()), style = MaterialTheme.typography.bodyMedium)
                        Slider(
                            value = speedState,
                            onValueChange = { speedState = it },
                            onValueChangeFinished = { viewModel.setSpeed(speedState.toDouble(), accelState.toDouble()) },
                            valueRange = 10f..500f
                        )
                        Text(stringResource(R.string.accel, accelState.toInt().toString()), style = MaterialTheme.typography.bodyMedium)
                        Slider(
                            value = accelState,
                            onValueChange = { accelState = it },
                            onValueChangeFinished = { viewModel.setSpeed(speedState.toDouble(), accelState.toDouble()) },
                            valueRange = 100f..2000f
                        )
                    }
                }

                if (selectedRobot.hasAtc) {
                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Box(modifier = Modifier.fillMaxWidth().metallicIndustrial()) {
                        Column {
                            Text(stringResource(R.string.current_tool_atc), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            ExposedDropdownMenuBox(
                                expanded = expandedTool,
                                onExpandedChange = { expandedTool = !expandedTool },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                OutlinedTextField(
                                    value = selectedRobot.currentTool,
                                    onValueChange = {},
                                    readOnly = true,
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedTool) },
                                    modifier = Modifier.menuAnchor(type = MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                                )
                                ExposedDropdownMenu(
                                    expanded = expandedTool,
                                    onDismissRequest = { expandedTool = false }
                                ) {
                                    selectedRobot.atcTools.forEach { tool ->
                                        DropdownMenuItem(
                                            text = { Text("[${stringResource(R.string.slot_label, tool.slot)}] ${tool.name}") },
                                            onClick = {
                                                viewModel.changeTool(tool.slot)
                                                expandedTool = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))
            } else {
                Text(stringResource(R.string.connect_server_to_see), style = MaterialTheme.typography.bodyMedium)
            }
        }

        // Fixed Playback controls at the bottom
        if (selectedRobot != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(16.dp)
                    .metallicIndustrial(backgroundColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HydraButton(
                        text = "", // Icon only
                        icon = Icons.Default.PlayArrow,
                        onClick = { viewModel.sendCommand("play") },
                        enabled = selectedRobot.online && !selectedRobot.isPlaying,
                        backgroundColor = Color(0xFF2E7D32),
                        modifier = Modifier.size(56.dp)
                    )
                    HydraButton(
                        text = "", // Icon only
                        icon = Icons.Default.Pause,
                        onClick = { viewModel.sendCommand("pause") },
                        enabled = selectedRobot.online && selectedRobot.isPlaying,
                        backgroundColor = Color(0xFFF9A825),
                        modifier = Modifier.size(56.dp)
                    )
                    HydraButton(
                        text = "", // Icon only
                        icon = Icons.Default.Stop,
                        onClick = { viewModel.sendCommand("stop") },
                        enabled = selectedRobot.online,
                        backgroundColor = IndustrialDanger,
                        modifier = Modifier.size(56.dp)
                    )
                }
            }
        }
    }
}
