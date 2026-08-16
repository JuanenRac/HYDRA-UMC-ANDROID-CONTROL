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

@Composable
fun CustomSplashScreen(onTimeout: () -> Unit) {
    var startFadeOut by remember { mutableStateOf(false) }

    // El fade out durará 2500ms (2.5s)
    val alphaAnim by animateFloatAsState(
        targetValue = if (startFadeOut) 0f else 1f,
        animationSpec = tween(durationMillis = 2500),
        label = "splashAlpha"
    )

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
