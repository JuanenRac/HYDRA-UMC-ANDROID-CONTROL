// =============================================================================
// HYDRA-UMC CONTROL - Global STOP button and its result banner
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0 - see LICENSE
// =============================================================================
package com.hydraumc.control.ui

import android.content.Context
import android.os.Vibrator
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hydraumc.control.R
import com.hydraumc.control.network.StopAllResult
import com.hydraumc.control.network.StopAllStatus
import com.hydraumc.control.viewmodel.RobotViewModel
import kotlinx.coroutines.delay

private val StopRed = Color(0xFFD50000)
private val WarnAmber = Color(0xFFB45309)

/** How long a clean "everything stopped" banner stays before it hides itself. */
private const val CLEAN_BANNER_MS = 6_000L

/**
 * The global STOP in the app bar: one tap, no confirmation, always enabled.
 * It stops every robot through the server's stop-all route (see
 * [RobotViewModel.globalStop]). The label stays the universal word STOP.
 */
@Composable
fun GlobalStopButton(viewModel: RobotViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val vibrator = remember { context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator }
    val description = stringResource(R.string.stop_all_description)
    val pulse = rememberInfiniteTransition(label = "stop-all-pulse")
    val glow by pulse.animateFloat(
        initialValue = 0.75f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 700, easing = LinearEasing), RepeatMode.Reverse),
        label = "stop-all-glow",
    )
    Column(
        modifier = modifier
            .height(40.dp)
            .widthIn(min = 64.dp)
            .background(StopRed.copy(alpha = glow), RoundedCornerShape(8.dp))
            .clickable {
                vibrateOn(vibrator, longArrayOf(0, 80, 40, 80))
                viewModel.globalStop()
            }
            .semantics { contentDescription = description }
            .padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            stringResource(R.string.stop_all_label),
            color = Color.White,
            fontWeight = FontWeight.Black,
            fontSize = 15.sp,
            maxLines = 1,
        )
        Text(
            stringResource(R.string.stop_all_caption),
            color = Color.White,
            fontSize = 9.sp,
            maxLines = 1,
        )
    }
}

/**
 * What the last global STOP did: sending, a clean result (hides itself), a
 * warning (a robot did not stop, or the stopped state was not saved; stays until
 * dismissed) or "not confirmed" (stays until a later STOP works).
 */
@Composable
fun StopAllBanner(viewModel: RobotViewModel, modifier: Modifier = Modifier) {
    val status = viewModel.stopAllStatus.value ?: return
    if (status is StopAllStatus.Done && status.result.isClean) {
        LaunchedEffect(status) {
            delay(CLEAN_BANNER_MS)
            viewModel.dismissStopAllStatus()
        }
    }
    val (color, lines) = when (status) {
        is StopAllStatus.Sending -> WarnAmber to listOf(stringResource(R.string.stop_all_sending))
        is StopAllStatus.NotConfirmed -> StopRed to listOfNotNull(
            stringResource(R.string.stop_all_not_confirmed),
            status.reason?.takeIf { it.isNotBlank() },
        )
        is StopAllStatus.Done -> {
            val result = status.result
            if (result.isClean) Color(0xFF15803D) to bannerLines(result) else StopRed to bannerLines(result)
        }
    }
    val dismissible = status is StopAllStatus.Done
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(color)
            .let { if (dismissible) it.clickable { viewModel.dismissStopAllStatus() } else it }
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        lines.forEachIndexed { i, line ->
            Text(
                line,
                color = Color.White,
                style = if (i == 0) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodySmall,
                fontWeight = if (i == 0) FontWeight.Bold else FontWeight.Normal,
            )
        }
    }
}

@Composable
private fun bannerLines(result: StopAllResult): List<String> {
    val lines = mutableListOf<String>()
    lines += if (result.isClean) {
        stringResource(R.string.stop_all_done, result.stoppedCount)
    } else {
        stringResource(R.string.stop_all_incomplete, result.stoppedCount, result.failedCount)
    }
    if (!result.persisted) lines += stringResource(R.string.stop_all_not_saved)
    if (result.viaFallback) lines += stringResource(R.string.stop_all_one_by_one)
    result.failedRobots.forEach { r ->
        lines += stringResource(R.string.stop_all_robot_failed, r.name.ifBlank { "#${r.robotId}" }, r.error ?: "-")
    }
    result.robots.filter { it.reservedBy != null }.forEach { r ->
        lines += stringResource(R.string.stop_all_robot_reserved, r.name.ifBlank { "#${r.robotId}" }, r.reservedBy ?: "")
    }
    return lines
}
