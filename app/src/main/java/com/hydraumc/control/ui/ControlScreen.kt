package com.hydraumc.control.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import com.hydraumc.control.viewmodel.RobotViewModel
import com.hydraumc.control.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ControlScreen(viewModel: RobotViewModel) {
    val robots = viewModel.robots.value
    val selectedId = viewModel.selectedRobotId.value
    val connectionStatus = viewModel.connectionStatus.value
    val lastError = viewModel.lastError.value

    var expandedRobot by remember { mutableStateOf(false) }
    var expandedTool by remember { mutableStateOf(false) }
    var stepSize by remember { mutableStateOf(10.0) }
    var activeTarget by remember { mutableStateOf("robot") }
    
    val selectedRobot = robots.find { it.id == selectedId }
    
    var speedState by remember(selectedRobot?.speed) { mutableStateOf(selectedRobot?.speed?.toFloat() ?: 100f) }
    var accelState by remember(selectedRobot?.acceleration) { mutableStateOf(selectedRobot?.acceleration?.toFloat() ?: 500f) }

    LaunchedEffect(selectedRobot) {
        if (selectedRobot?.hasXYTable == false) {
            activeTarget = "robot"
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(stringResource(R.string.control_robots), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.status_label) + connectionStatus, style = MaterialTheme.typography.bodyMedium, color = if(connectionStatus == stringResource(R.string.status_connected)) Color(0xFF2E7D32) else Color.Red)
        if (lastError != null) {
            Text(lastError, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }

        Spacer(modifier = Modifier.height(16.dp))
        
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
                modifier = Modifier.menuAnchor().fillMaxWidth(),
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
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Button(onClick = { viewModel.sendCommand("enable") }, enabled = !selectedRobot.online) { Text(stringResource(R.string.enable)) }
                Button(onClick = { viewModel.sendCommand("disable") }, enabled = selectedRobot.online, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text(stringResource(R.string.disable)) }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            Divider()
            Spacer(modifier = Modifier.height(16.dp))
            
            // Joystick Section
            Text(stringResource(R.string.virtual_joystick), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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
            Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Button(onClick = { viewModel.jog(activeTarget, "y", stepSize) }, enabled = selectedRobot.online) { Text("Y+") }
                    Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                        Button(onClick = { viewModel.jog(activeTarget, "x", -stepSize) }, enabled = selectedRobot.online) { Text("X-") }
                        Button(onClick = { viewModel.jog(activeTarget, "x", stepSize) }, enabled = selectedRobot.online) { Text("X+") }
                    }
                    Button(onClick = { viewModel.jog(activeTarget, "y", -stepSize) }, enabled = selectedRobot.online) { Text("Y-") }
                }
                if (activeTarget == "robot") {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Button(onClick = { viewModel.jog(activeTarget, "z", stepSize) }, enabled = selectedRobot.online) { Text("Z+") }
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { viewModel.jog(activeTarget, "z", -stepSize) }, enabled = selectedRobot.online) { Text("Z-") }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Divider()
            Spacer(modifier = Modifier.height(16.dp))
            
            // Kinematics / Settings
            Text(stringResource(R.string.kinematics), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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

            if (selectedRobot.hasAtc) {
                Spacer(modifier = Modifier.height(16.dp))
                Divider()
                Spacer(modifier = Modifier.height(16.dp))
                Text(stringResource(R.string.current_tool_atc), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                        colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
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

            Spacer(modifier = Modifier.height(24.dp))
            Divider()
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(stringResource(R.string.auto_playback), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(top = 8.dp)) {
                Button(onClick = { viewModel.sendCommand("play") }, enabled = selectedRobot.online && !selectedRobot.isPlaying) { Text("▶ " + stringResource(R.string.play)) }
                Button(onClick = { viewModel.sendCommand("pause") }, enabled = selectedRobot.online && selectedRobot.isPlaying) { Text("⏸ " + stringResource(R.string.pause)) }
                Button(onClick = { viewModel.sendCommand("stop") }, enabled = selectedRobot.online) { Text("⏹ " + stringResource(R.string.stop)) }
            }
            Spacer(modifier = Modifier.height(32.dp))
        } else {
            Text(stringResource(R.string.connect_server_to_see), style = MaterialTheme.typography.bodyMedium)
        }
    }
}
