// =============================================================================
// HYDRA-UMC CONTROL - Custom modifiers for industrial-themed 3D effects
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0 - see LICENSE
// =============================================================================
package com.hydraumc.control.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * A modifier that applies a 3D metallic industrial look to a container.
 * @param backgroundColor The primary background color.
 * @param borderColor The border accent color.
 * @return The modified Modifier chain.
 */
@Composable
fun Modifier.metallicIndustrial(
    backgroundColor: Color = MetallicBlueMedium,
    borderColor: Color = MetallicBlueLight,
): Modifier = this
    .shadow(
        elevation = 8.dp,
        shape = RoundedCornerShape(12.dp),
        ambientColor = Color.Black.copy(alpha = 0.5f),
        spotColor = Color.Black,
    )
    .border(
        width = 1.dp,
        brush = Brush.verticalGradient(
            colors = listOf(
                borderColor.copy(alpha = 0.8f),
                Color.Black.copy(alpha = 0.3f),
                borderColor.copy(alpha = 0.5f)
            )
        ),
        shape = RoundedCornerShape(12.dp)
    )
    .clip(RoundedCornerShape(12.dp))
    .background(
        brush = Brush.verticalGradient(
            colors = listOf(
                backgroundColor,
                backgroundColor.copy(alpha = 0.9f),
                Color.Black.copy(alpha = 0.4f)
            )
        )
    )
    .padding(12.dp)

/**
 * A modifier that applies a 3D metallic industrial look to a button, with press states.
 * @param backgroundColor The primary background color.
 * @param borderColor The border highlight color.
 * @param pressed Whether the button is currently being pressed.
 * @return The modified Modifier chain.
 */
@Composable
fun Modifier.metallicButton(
    backgroundColor: Color = DarkPrimary,
    borderColor: Color = Color.White.copy(alpha = 0.3f),
    pressed: Boolean = false
): Modifier = this
    .shadow(
        elevation = if (pressed) 2.dp else 6.dp,
        shape = RoundedCornerShape(8.dp)
    )
    .border(
        width = 1.dp,
        brush = Brush.verticalGradient(
            colors = listOf(
                borderColor,
                Color.Transparent,
                Color.Black.copy(alpha = 0.5f)
            )
        ),
        shape = RoundedCornerShape(8.dp)
    )
    .clip(RoundedCornerShape(8.dp))
    .background(
        brush = Brush.verticalGradient(
            colors = if (pressed) {
                listOf(backgroundColor.copy(alpha = 0.8f), backgroundColor)
            } else {
                listOf(backgroundColor, backgroundColor.copy(alpha = 0.7f))
            }
        )
    )
    .padding(horizontal = 16.dp, vertical = 8.dp)
