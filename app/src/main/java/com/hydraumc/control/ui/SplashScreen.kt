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
        animationSpec = tween(durationMillis = 700),
        label = "splashAlpha"
    )

    // Real complaint this fixed: the original 5000ms hold + 2500ms fade
    // (7.5s total) made every cold start feel stuck - the fade's own tail
    // reads as a plain black screen for a couple of those seconds. That
    // was fixed down to 900ms + 400ms, which the owner then tested live
    // and reported as feeling TOO abrupt in the other direction ("sin
    // transición") - 900ms barely reads as a splash at all, and 400ms is
    // too quick a cut to feel like a real fade. Retuned once more from
    // real feedback rather than guessing again: a bit more hold (still
    // nowhere near the original 5s), and a longer, genuinely visible fade.
    // Nothing here gates on real work finishing (that's MainActivity's own
    // authCheckComplete wait, layered on top of this) - purely a branding
    // timing choice either way.
    LaunchedEffect(Unit) {
        delay(2200)
        startFadeOut = true
        delay(700)
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
