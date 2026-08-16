// =============================================================================
// HYDRA-UMC CONTROL - UI screen for displaying robot camera feeds
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0 - see LICENSE
// =============================================================================
package com.hydraumc.control.ui

import androidx.compose.foundation.layout.*
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
 * Allows selecting between 8 different robot camera feeds.
 * 
 * @param viewModel The shared RobotViewModel.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraScreen(viewModel: RobotViewModel) {
    var expanded by remember { mutableStateOf(false) }
    val selectedCameraId by viewModel.selectedCameraId

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        /** Camera Selector Dropdown */
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded },
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = "Robot Camera $selectedCameraId",
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
                (1..8).forEach { id ->
                    DropdownMenuItem(
                        text = { Text("Robot Camera $id") },
                        onClick = {
                            viewModel.selectedCameraId.intValue = id
                            expanded = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        /** Placeholder box representing the video feed area. */
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .metallicIndustrial(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "FEED: ROBOT $selectedCameraId",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                CircularProgressIndicator(color = MaterialTheme.colorScheme.secondary)
            }
        }
    }
}
