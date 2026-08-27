// =============================================================================
// HYDRA-UMC CONTROL - Custom animated splash screen for brand identity
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0 - see LICENSE
// =============================================================================
package com.hydraumc.control.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.hydraumc.control.R
import kotlinx.coroutines.delay

/**
 * Composable that renders a custom splash screen with a fade-out animation.
 * 
 * @param onTimeout Callback triggered when the splash animation completes.
 */
@Composable
fun CustomSplashScreen(onTimeout: () -> Unit) {
    /** State flag to initiate the fade-out effect. */
    var startFadeOut by remember { mutableStateOf(false) }

    /** Animated alpha value for the splash screen transition. */
    val alphaAnim by animateFloatAsState(
        targetValue = if (startFadeOut) 0f else 1f,
        animationSpec = tween(durationMillis = 2500),
        label = "splashAlpha"
    )

    /** Effect to manage the splash screen timing and transitions. */
    LaunchedEffect(Unit) {
        delay(5000) // Mostrar durante 5 segundos
        startFadeOut = true
        delay(2500) // Esperar a que termine la animación
        onTimeout()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .alpha(alphaAnim)
    ) {
        Image(
            painter = painterResource(id = R.drawable.splash_bg),
            contentDescription = "Splash Screen",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    }
}
