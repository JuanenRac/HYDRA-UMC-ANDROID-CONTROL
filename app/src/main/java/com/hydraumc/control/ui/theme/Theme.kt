// =============================================================================
// HYDRA-UMC CONTROL - Main application theme using a dark industrial style
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0 - see LICENSE
// =============================================================================
package com.hydraumc.control.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/** 
 * Default dark color scheme for the HYDRA-UMC industrial theme. 
 */
private val DarkColorScheme = darkColorScheme(
    primary = DarkPrimary,
    secondary = DarkSecondary,
    tertiary = MetallicCyan,
    background = DarkBackground,
    surface = DarkSurface,
    onPrimary = DarkOnSurface,
    onSecondary = DarkOnSurface,
    onTertiary = DarkOnSurface,
    onBackground = DarkOnSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = MetallicBlueMedium,
    onSurfaceVariant = MetallicBlueHighlight,
)

/**
 * Main theme composable for the application.
 * Forces a dark industrial theme across the UI.
 * 
 * @param darkTheme Whether to use dark theme (defaults to true).
 * @param content The composable content to be themed.
 */
@Composable
fun HydraTheme(
    darkTheme: Boolean = true, // Forced dark theme
    content: @Composable () -> Unit
) {
    /** The selected color scheme for the theme. */
    val colorScheme = DarkColorScheme // Forced dark industrial theme
    /** The current view instance. */
    val view = LocalView.current
    
    /** Rationale: Apply status bar color based on the theme. */
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
