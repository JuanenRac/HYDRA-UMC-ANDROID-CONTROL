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
 * A modifier that applies a 3D metallic industrial look to a component.
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
        spotColor = Color.Black
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
 * A modifier for buttons to give them a "pressed" or "beveled" industrial look.
 */
@Composable
fun Modifier.industrialButton(
    primaryColor: Color = DarkPrimary
): Modifier = this
    .shadow(4.dp, RoundedCornerShape(8.dp))
    .background(
        brush = Brush.verticalGradient(
            colors = listOf(
                primaryColor,
                primaryColor.copy(alpha = 0.8f)
            )
        ),
        shape = RoundedCornerShape(8.dp)
    )
    .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
    .padding(horizontal = 16.dp, vertical = 8.dp)
