// =============================================================================
// HYDRA-UMC CONTROL - Reusable industrial-themed UI components
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0 - see LICENSE
// =============================================================================
package com.hydraumc.control.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * An industrial-style LED indicator for status reporting.
 * 
 * @param isOn Whether the LED is illuminated.
 * @param activeColor The color when the LED is ON.
 * @param inactiveColor The color when the LED is OFF.
 * @param label Optional text label to display next to the LED.
 * @param size The diameter of the LED.
 */
@Composable
fun StatusLed(
    isOn: Boolean,
    activeColor: Color = MetallicCyan,
    inactiveColor: Color = Color(0xFF222222),
    label: String? = null,
    size: Dp = 12.dp,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .shadow(if (isOn) 4.dp else 0.dp, CircleShape)
                .border(1.dp, Color.White.copy(alpha = 0.1f), CircleShape)
                .clip(CircleShape)
                .background(
                    brush = Brush.radialGradient(
                        colors = if (isOn) {
                            listOf(activeColor.copy(alpha = 0.9f), activeColor.copy(alpha = 0.4f))
                        } else {
                            listOf(inactiveColor, Color.Black)
                        }
                    )
                )
        ) {
            if (isOn) {
                // Glow effect
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(4.dp)
                        .background(activeColor.copy(alpha = 0.3f))
                )
            }
        }
        
        label?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelSmall,
                color = if (isOn) activeColor else MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp
            )
        }
    }
}
