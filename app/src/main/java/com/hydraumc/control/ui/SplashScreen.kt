// =============================================================================
// HYDRA-UMC CONTROL - Start-up screen: the mark appears, then the app
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hydraumc.control.R
import com.hydraumc.control.ui.theme.DarkBackground
import com.hydraumc.control.ui.theme.IndustrialSteel
import kotlinx.coroutines.delay

/** Dark backdrop shared by the start-up screen and the sign-in. */
val StartupBackdrop: Brush
    get() = Brush.radialGradient(listOf(Color(0xFF0B3A47), DarkBackground), radius = 900f)

/**
 * The first second and a half: the mark appears, then the app. No picture, no long wait:
 * the same quiet start-up as the other apps of the family.
 *
 * @param onTimeout Callback triggered when the animation completes.
 */
@Composable
fun CustomSplashScreen(onTimeout: () -> Unit) {
    val appear = remember { Animatable(0f) }
    val leave = remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        appear.animateTo(1f, tween(650))
        delay(650)
        leave.animateTo(0f, tween(350))
        onTimeout()
    }
    Box(
        modifier = Modifier.fillMaxSize().alpha(leave.value).background(StartupBackdrop),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.alpha(appear.value).scale(0.85f + 0.15f * appear.value),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_hydra_logo),
                contentDescription = null,
                modifier = Modifier.size(112.dp),
            )
            Spacer(Modifier.height(20.dp))
            Text("HYDRA-UMC", fontSize = 30.sp, fontWeight = FontWeight.Black, letterSpacing = 6.sp, color = Color(0xFFEDF7FF))
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.splash_tagline), color = IndustrialSteel, fontSize = 14.sp)
        }
    }
}
