// =============================================================================
// HYDRA-UMC CONTROL - UI screen for displaying robot camera feeds
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0 - see LICENSE
// =============================================================================
package com.hydraumc.control.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hydraumc.control.R
import com.hydraumc.control.viewmodel.RobotViewModel
import com.hydraumc.control.ui.theme.metallicIndustrial

/**
 * Composable that displays the Camera screen.
 * currently acts as a placeholder for future camera integration.
 * 
 * @param viewModel The shared RobotViewModel.
 */
@Suppress("UNUSED_PARAMETER")
@Composable
fun CameraScreen(viewModel: RobotViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        /** Placeholder box representing the video feed area. */
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
                .metallicIndustrial(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "CAMERA STREAMING",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        /** Informational text regarding future development. */
        Text(
            text = "Video feed placeholder. Integration with CM5 camera server coming soon.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
