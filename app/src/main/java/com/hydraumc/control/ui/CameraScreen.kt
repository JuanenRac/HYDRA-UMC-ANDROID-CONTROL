// =============================================================================
// HYDRA-UMC CONTROL - UI screen for displaying robot camera feeds
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0 - see LICENSE
// =============================================================================
package com.hydraumc.control.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.hydraumc.control.viewmodel.RobotViewModel
import com.hydraumc.control.ui.theme.metallicIndustrial

/**
 * Composable that displays the Camera screen.
 * Allows selecting between the robots the server actually reports, and shows
 * a real MJPEG stream from the CM5 server for whichever one is selected.
 * Lists only robots the server reports (via RobotState.hasCamera - see
 * model/HydraState.kt), and lets the operator turn a robot's camera on/off
 * directly from here, instead of assuming every robot has one.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraScreen(viewModel: RobotViewModel) {
    var expanded by remember { mutableStateOf(false) }
    val selectedCameraId by viewModel.selectedCameraId
    val robots = viewModel.robots.value
    val ip = viewModel.ipAddress.value
    val port = viewModel.port.value

    val selectedRobot = robots.find { it.id == selectedCameraId }
    val cameraEnabled = selectedRobot?.hasCamera ?: false

    // Industrial MJPEG stream URL
    val streamUrl = "http://$ip:$port/api/camera/$selectedCameraId/stream"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        /** Camera Selector Dropdown - lists real robots, not a fixed 1..8 range. */
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded },
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = selectedRobot?.name ?: "Robot Camera $selectedCameraId",
                onValueChange = {},
                readOnly = true,
                label = { Text("Select Camera Feed") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .menuAnchor(type = MenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                robots.forEach { robot ->
                    DropdownMenuItem(
                        text = { Text("${robot.name}${if (!robot.hasCamera) " (disabled)" else ""}") },
                        onClick = {
                            viewModel.selectedCameraId.intValue = robot.id
                            expanded = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (cameraEnabled) "Camera Enabled" else "Camera Disabled",
                style = MaterialTheme.typography.labelMedium,
                color = if (cameraEnabled) Color(0xFF4CAF50) else Color(0xFFEF5350)
            )
            Switch(
                checked = cameraEnabled,
                enabled = selectedRobot != null,
                onCheckedChange = { enabled ->
                    selectedRobot?.let { viewModel.setVisionEnabled(it.id, enabled) }
                }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        /** Real MJPEG Stream Viewer, or a clear "disabled" placeholder instead of a silently blank feed. */
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .metallicIndustrial(backgroundColor = Color.Black),
            contentAlignment = Alignment.Center
        ) {
            if (cameraEnabled) {
                MjpegPlayer(
                    url = streamUrl,
                    modifier = Modifier.fillMaxSize()
                )
                Text(
                    text = "LIVE: ${selectedRobot?.name ?: "ROBOT $selectedCameraId"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Green.copy(alpha = 0.7f),
                    modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
                )
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.VideocamOff, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Camera Disabled",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray
                    )
                    Text(
                        text = "Use the switch above to enable it on the server",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}
