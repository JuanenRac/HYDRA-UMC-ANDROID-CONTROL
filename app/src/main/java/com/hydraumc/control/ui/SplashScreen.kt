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
        animationSpec = tween(durationMillis = 400),
        label = "splashAlpha"
    )

    // Real complaint this fixes, live-reproduced: the original 5000ms hold
    // + 2500ms fade (7.5s total) made every cold start feel stuck - the
    // fade's own tail (image alpha dropping toward 0 against this Box's
    // solid Color.Black background) reads as a plain black screen for a
    // couple of those seconds, which is what got reported as "queda
    // negro". Nothing here was ever gating on real work finishing (that's
    // MainActivity's own authCheckComplete wait, layered on top of this) -
    // it was purely a fixed branding delay, so shortening it is a pure UX
    // win with no correctness change.
    LaunchedEffect(Unit) {
        delay(900)
        startFadeOut = true
        delay(400)
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
