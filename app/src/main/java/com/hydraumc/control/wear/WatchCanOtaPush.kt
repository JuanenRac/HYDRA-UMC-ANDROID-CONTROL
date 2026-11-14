// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - Passes the live progress of a flash cycle to the paired watch
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.wear

import android.content.Context
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.Wearable
import com.hydraumc.control.model.CanOtaProgress
import com.hydraumc.control.network.RobotCommandContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/** Where the progress of a flash cycle goes (the Wear Data Layer in the app, a fake in the tests). */
fun interface WatchCanOtaSink {
    /** Returns true when at least one watch took the message. */
    suspend fun send(progress: WatchCanOtaProgress): Boolean
}

/**
 * Decides which reports of a flash cycle are worth a message to the wrist, so a transfer of hundreds of pages does not flood the link: the first one,
 * every change of phase, the end of the cycle, and a change of the percent of at least [MIN_STEP_PERCENT] points when [MIN_GAP_MS] have passed
 * since the last one sent. It holds no network and no Android class.
 */
class CanOtaWatchPolicy {
    private var lastPhase: String? = null
    private var lastPercent: Int? = null
    private var lastSentAtMs: Long = 0L

    /** True when [next] should be sent now; it is then remembered as the last one sent. */
    fun accept(next: CanOtaProgress, nowMs: Long): Boolean {
        val phase = next.phase.wire
        val send = when {
            lastPhase == null -> true
            phase != lastPhase -> true
            next.finished -> true
            else -> {
                val last = lastPercent
                val now = next.percent
                now != null && (last == null || kotlin.math.abs(now - last) >= MIN_STEP_PERCENT) && nowMs - lastSentAtMs >= MIN_GAP_MS
            }
        }
        if (send) {
            lastPhase = phase
            lastPercent = next.percent
            lastSentAtMs = nowMs
        }
        return send
    }

    /** Forgets what was sent (the socket closed): the next report is sent whatever it is. */
    fun reset() {
        lastPhase = null
        lastPercent = null
        lastSentAtMs = 0L
    }

    companion object {
        const val MIN_STEP_PERCENT = 5
        const val MIN_GAP_MS = 1_000L
    }
}

/**
 * Sends the progress as a message on `/hydra-umc/canota-progress/v1` to every watch connected to this phone. A message is not stored: when no watch is
 * connected it is not delivered, and nothing is queued, because an old progress on the wrist is worse than none.
 */
class WearCanOtaSink(private val context: Context) : WatchCanOtaSink {
    override suspend fun send(progress: WatchCanOtaProgress): Boolean = withContext(Dispatchers.IO) {
        val timeout = RobotCommandContract.WATCH_ALERTS_SEND_TIMEOUT_MS
        try {
            val nodes = Tasks.await(Wearable.getNodeClient(context).connectedNodes, timeout, TimeUnit.MILLISECONDS)
            val payload = progress.toJson().toString().encodeToByteArray()
            var delivered = false
            for (node in nodes) {
                try {
                    Tasks.await(Wearable.getMessageClient(context).sendMessage(node.id, WatchRelayPaths.CANOTA_PROGRESS, payload), timeout, TimeUnit.MILLISECONDS)
                    delivered = true
                } catch (e: Exception) {
                    Log.w(TAG, "progress to ${node.id} failed (${e.javaClass.simpleName})")
                }
            }
            delivered
        } catch (e: Exception) {
            Log.w(TAG, "no watch for the progress (${e.javaClass.simpleName})")
            false
        }
    }

    private companion object {
        const val TAG = "WearCanOtaSink"
    }
}
