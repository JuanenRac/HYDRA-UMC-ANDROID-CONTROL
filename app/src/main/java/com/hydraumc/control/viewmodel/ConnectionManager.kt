// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - viewmodel/ConnectionManager.kt
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.viewmodel

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import kotlin.coroutines.cancellation.CancellationException

/**
 * The live side of the connection, away from the view model: the poll of the system metrics (CPU, memory, temperature,
 * uptime). It runs while [shouldPoll] says so (connected and the app in the foreground: the socket stays alive in the
 * background, the poll does not need to) and starting it again cancels the previous loop, so a reconnect never leaves an
 * old loop racing the new one to write the same value. A failed read is skipped and tried again at the next turn.
 */
class ConnectionManager(
    private val scope: CoroutineScope,
    private val shouldPoll: () -> Boolean,
    private val onMetrics: (SystemMetrics) -> Unit,
    private val intervalMs: Long = 5000,
) {
    private var metricsJob: Job? = null

    val isPolling: Boolean get() = metricsJob?.isActive == true

    fun startMetrics(fetch: suspend () -> JSONObject) {
        metricsJob?.cancel()
        metricsJob = scope.launch {
            while (shouldPoll()) {
                try {
                    val m = fetch()
                    onMetrics(
                        SystemMetrics(
                            cpuLoad = m.optInt("cpu_load"),
                            memoryUsage = m.optInt("memory_usage"),
                            temp = m.optDouble("temp"),
                            uptime = m.optInt("uptime"),
                        ),
                    )
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                }
                delay(intervalMs)
            }
        }
    }

    fun stopMetrics() {
        metricsJob?.cancel()
        metricsJob = null
    }
}
