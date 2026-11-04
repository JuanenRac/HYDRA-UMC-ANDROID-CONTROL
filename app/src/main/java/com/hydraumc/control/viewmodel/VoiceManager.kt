// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - viewmodel/VoiceManager.kt
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.viewmodel

import androidx.compose.runtime.mutableStateOf
import com.hydraumc.control.network.HydraApiClient
import com.hydraumc.control.wear.WatchAssistantReply
import com.hydraumc.control.wear.WatchSystemStatus
import com.hydraumc.control.wear.WatchVoiceTurn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The voice assistant of the app, away from the view model: a recognised (or typed) transcript goes through the
 * authenticated server boundary (POST /api/voice/turn, tagged as this phone's own traffic with postVoiceTurn(), not
 * postWatchVoiceTurn(): the two must stay independent). A reply only ever carries text and status; it never sends a
 * robot command or changes robot state. It also holds the safe health card the Wear relay asks for.
 * The client, the error line and the log are handed in so the screen keeps reading the same state.
 */
class VoiceManager(
    private val scope: CoroutineScope,
    private val client: () -> HydraApiClient?,
    private val reportError: (String) -> Unit,
    private val log: (String) -> Unit,
) {
    val latestReply = mutableStateOf<WatchAssistantReply?>(null)
    val busy = mutableStateOf(false)
    val latestWatchSystemStatus = mutableStateOf<WatchSystemStatus?>(null)

    fun sendTurn(transcript: String) {
        val api = client() ?: run {
            reportError("Connect to HYDRA-UMC-SERVER before using the voice assistant.")
            return
        }
        val requestId = "android-${System.currentTimeMillis()}"
        val locale = java.util.Locale.getDefault().toLanguageTag()
        busy.value = true
        scope.launch {
            try {
                val reply = api.postVoiceTurn(WatchVoiceTurn(requestId, transcript, locale))
                latestReply.value = reply
                log("Voice assistant reply received: ${reply.level}; confirmation=${reply.requiresConfirmation}")
            } catch (error: Exception) {
                reportError("Voice assistant request failed: ${error.message}")
                log("Voice assistant request failed")
            } finally {
                busy.value = false
            }
        }
    }

    fun clearReply() {
        latestReply.value = null
    }

    fun refreshWatchSystemStatus() {
        val api = client() ?: return
        scope.launch {
            try {
                latestWatchSystemStatus.value = api.getWatchSystemStatus()
            } catch (error: Exception) {
                log("Watch system-status refresh failed")
            }
        }
    }
}
