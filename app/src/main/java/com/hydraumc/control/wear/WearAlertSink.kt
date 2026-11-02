// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - Sends an alert to the paired watch through the Wear Data Layer
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

/**
 * Sends the alert as a message on `/hydra-umc/alert/v1` to every watch connected to this phone. A message is not stored: when no
 * watch is connected it is simply not delivered (the call says so), and nothing is queued for later, because an old alert on the
 * wrist is worse than none. The text is in the language of the phone.
 */
class WearAlertSink(private val context: Context) : WatchAlertSink {
    override suspend fun send(alert: WatchAlert): Boolean = withContext(Dispatchers.IO) {
        val timeout = RobotCommandContract.WATCH_ALERTS_SEND_TIMEOUT_MS
        try {
            val nodes = Tasks.await(Wearable.getNodeClient(context).connectedNodes, timeout, TimeUnit.MILLISECONDS)
            val payload = alert.toJson().toString().encodeToByteArray()
            var delivered = false
            for (node in nodes) {
                try {
                    Tasks.await(Wearable.getMessageClient(context).sendMessage(node.id, WatchBridgePaths.ALERT, payload), timeout, TimeUnit.MILLISECONDS)
                    delivered = true
                } catch (e: Exception) {
                    Log.w(TAG, "alert to ${node.id} failed (${e.javaClass.simpleName})")
                }
            }
            delivered
        } catch (e: Exception) {
            Log.w(TAG, "no watch to alert (${e.javaClass.simpleName})")
            false
        }
    }

    private companion object {
        const val TAG = "WearAlertSink"
    }
}
