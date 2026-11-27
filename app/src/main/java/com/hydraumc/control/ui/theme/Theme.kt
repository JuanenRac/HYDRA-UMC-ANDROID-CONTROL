// =============================================================================
// HYDRA-UMC CONTROL - The look of the app: the colours and the theme
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * The few colours every screen uses. The family look is a near-black backdrop, raised cards with a thin border,
 * one accent and amber or red only for attention; the accent of this app is its metallic blue.
 */
object HydraColors {
    val Background = DarkBackground
    val Surface = DarkSurface
    val SurfaceRaised = MetallicBlueMedium
    val Outline = Color(0xFF33486A)
    /** The metallic blue of the app: filled buttons and highlights. */
    val Primary = DarkPrimary
    /** The same blue, lighter, for icons and links on the dark backdrop. */
    val Accent = Color(0xFF2FB4F0)
    val Amber = Color(0xFFFFB020)
    val Alert = Color(0xFFFF4D5E)
    val Ok = Color(0xFF3DDC97)
    val Text = Color(0xFFEDF7FF)
    val Muted = Color(0xFF91A8BD)
    /** The red of the STOP: the same on every screen and window. */
    val Stop = Color(0xFFD50000)
}

private val DarkColorScheme = darkColorScheme(
    primary = HydraColors.Primary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF0B3550),
    onPrimaryContainer = HydraColors.Text,
    secondary = HydraColors.Accent,
    onSecondary = Color(0xFF031521),
    secondaryContainer = Color(0xFF12303F),
    onSecondaryContainer = HydraColors.Text,
    tertiary = HydraColors.Amber,
    onTertiary = Color(0xFF1F1400),
    background = HydraColors.Background,
    onBackground = HydraColors.Text,
    surface = HydraColors.Surface,
    onSurface = HydraColors.Text,
    surfaceVariant = HydraColors.SurfaceRaised,
    onSurfaceVariant = HydraColors.Muted,
    surfaceContainerLowest = HydraColors.Background,
    surfaceContainerLow = HydraColors.Surface,
    surfaceContainer = HydraColors.Surface,
    surfaceContainerHigh = HydraColors.SurfaceRaised,
    surfaceContainerHighest = HydraColors.SurfaceRaised,
    outline = HydraColors.Outline,
    outlineVariant = Color(0xFF1B2836),
    error = HydraColors.Alert,
    onError = Color(0xFF2A0508),
    errorContainer = Color(0xFF3A1319),
    onErrorContainer = Color(0xFFFFD9DD),
)

/** The theme of the application: always dark, with the metallic blue of HYDRA-UMC. */
@Composable
fun HydraTheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val activity = view.context as? Activity ?: return@SideEffect
            @Suppress("DEPRECATION")
            activity.window.statusBarColor = DarkColorScheme.background.toArgb()
            WindowCompat.getInsetsController(activity.window, view).isAppearanceLightStatusBars = false
        }
    }
    MaterialTheme(colorScheme = DarkColorScheme, typography = Typography, content = content)
}
