// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - Tells the paired watch that the server is stopped only because it restarted
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.wear

import android.content.Context
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.Wearable
import com.hydraumc.control.network.RobotCommandContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/** Where the notice goes (the Wear Data Layer in the app, a fake in the tests). */
fun interface WatchStopRestoredSink {
    /** Returns true when at least one watch took the message. */
    suspend fun send(notice: WatchStopRestored): Boolean
}

/**
 * Decides when the watch is told: when the value changes, and the first time a connection says it; a repeat of the same value sends nothing.
 * It holds no network and no Android class.
 */
class StopRestoredWatchPolicy {
    private var last: Boolean? = null

    /** True when [restored] should be sent now; it is then remembered. */
    fun accept(restored: Boolean): Boolean {
        if (last == restored) return false
        last = restored
        return true
    }

    /** Whether the watch was last told that the stop was restored (so the notice can be cleared when the socket closes). */
    val toldRestored: Boolean get() = last == true

    /** Forgets what was told: the next value is sent whatever it is. */
    fun reset() {
        last = null
    }
}

/**
 * Sends the notice as a message on `/hydra-umc/stop-restored/v1` to every watch connected to this phone. A message is not stored: when no watch is
 * connected it is not delivered and nothing is queued, because an old notice on the wrist is worse than none.
 */
class WearStopRestoredSink(private val context: Context) : WatchStopRestoredSink {
    override suspend fun send(notice: WatchStopRestored): Boolean = withContext(Dispatchers.IO) {
        val timeout = RobotCommandContract.WATCH_ALERTS_SEND_TIMEOUT_MS
        try {
            val nodes = Tasks.await(Wearable.getNodeClient(context).connectedNodes, timeout, TimeUnit.MILLISECONDS)
            val payload = notice.toJson().toString().encodeToByteArray()
            var delivered = false
            for (node in nodes) {
                try {
                    Tasks.await(Wearable.getMessageClient(context).sendMessage(node.id, WatchRelayPaths.STOP_RESTORED, payload), timeout, TimeUnit.MILLISECONDS)
                    delivered = true
                } catch (e: Exception) {
                    Log.w(TAG, "notice to ${node.id} failed (${e.javaClass.simpleName})")
                }
            }
            delivered
        } catch (e: Exception) {
            Log.w(TAG, "no watch for the notice (${e.javaClass.simpleName})")
            false
        }
    }

    private companion object {
        const val TAG = "WearStopRestoredSink"
    }
}
