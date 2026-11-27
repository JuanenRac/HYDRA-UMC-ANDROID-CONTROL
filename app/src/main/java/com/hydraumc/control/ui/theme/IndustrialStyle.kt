// =============================================================================
// HYDRA-UMC CONTROL - Custom modifiers for industrial-themed 3D effects
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
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
 * The card of a screen as a modifier: the same shape, border and faint wash as [Panel], with its inner padding.
 * @param backgroundColor The primary background color.
 * @param borderColor The border color.
 * @return The modified Modifier chain.
 */
@Composable
fun Modifier.metallicIndustrial(
    backgroundColor: Color = HydraColors.SurfaceRaised,
    borderColor: Color = HydraColors.Outline,
): Modifier = this
    .clip(RoundedCornerShape(18.dp))
    .background(backgroundColor)
    .background(Brush.verticalGradient(listOf(HydraColors.Accent.copy(alpha = 0.04f), Color.Transparent)))
    .border(width = 1.dp, color = borderColor, shape = RoundedCornerShape(18.dp))
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
        shape = RoundedCornerShape(14.dp)
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
        shape = RoundedCornerShape(14.dp)
    )
    .clip(RoundedCornerShape(14.dp))
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
