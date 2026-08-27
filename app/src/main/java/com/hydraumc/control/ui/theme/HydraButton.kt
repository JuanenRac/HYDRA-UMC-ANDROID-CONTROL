// =============================================================================
// HYDRA-UMC CONTROL - Custom button component with industrial styling
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0 - see LICENSE
// =============================================================================
package com.hydraumc.control.ui.theme

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * A custom industrial-styled button for the HYDRA-UMC ecosystem.
 * 
 * @param text The text label for the button.
 * @param onClick The callback to execute when clicked.
 * @param modifier Optional modifier for the button.
 * @param enabled Whether the button is interactable.
 * @param backgroundColor The base background color of the button.
 * @param icon Optional icon to display before the text.
 * @param textColor The color of the text and icon.
 */
@Composable
fun HydraButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    backgroundColor: Color = DarkPrimary,
    icon: ImageVector? = null,
    textColor: Color = Color.White
) {
    /** Rationale: Manage interaction source to track press states for 3D effects. */
    val interactionSource = remember { MutableInteractionSource() }
    /** Boolean state tracking if the button is currently being pressed. */
    val isPressed by interactionSource.collectIsPressedAsState()

    Box(
        modifier = modifier
            .then(
                if (enabled) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onClick
                    )
                } else Modifier
            )
            .metallicButton(
                backgroundColor = if (enabled) backgroundColor else Color.Gray,
                pressed = isPressed
            ),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = textColor,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = textColor.copy(alpha = if (enabled) 1f else 0.5f)
            )
        }
    }
}
