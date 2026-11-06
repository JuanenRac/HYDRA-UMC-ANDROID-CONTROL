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
import com.hydraumc.control.network.HydraApiException
import org.json.JSONObject
import kotlin.coroutines.cancellation.CancellationException

/** What the first synchronisation of a connection reads from the server (the REST client in the app, a fake in the tests). */
interface ConnectSource {
    /** True when the server is reached over TLS. */
    val usesTls: Boolean

    /** The server's identity answer, or null when it gave none. */
    suspend fun hydraInfo(): JSONObject?

    /** The whole settings tree. */
    suspend fun settings(): JSONObject

    /** Renews an expired session once; true when the next read can be tried again. */
    suspend fun renewSession(): Boolean
}

/** What the connection tells the rest of the app, in the order it happens. Every call has an empty default. */
interface ConnectListener {
    fun log(message: String) {}
    fun onInfo(info: JSONObject) {}
    fun onReachable(usesTls: Boolean) {}
    fun onSettings(settings: JSONObject) {}
    fun onSynced() {}
    fun onSyncFailed(error: HydraApiException) {}

    /** The REST sync worked: the live socket may open now. Not called against a server the sync could not reach. */
    fun openSocket() {}
}

/**
 * The live side of the connection, away from the view model: the connect sequence (one at a time: a new one cancels the
 * previous one, so two taps never interleave their writes), the "switching server" flag the socket callbacks read, and the
 * poll of the system metrics (CPU, memory, temperature, uptime). The poll runs while [shouldPoll] says so (connected and
 * the app in the foreground: the socket stays alive in the background, the poll does not need to) and starting it again
 * cancels the previous loop, so a reconnect never leaves an old loop racing the new one to write the same value. A failed
 * read is skipped and tried again at the next turn.
 */
class ConnectionManager(
    private val scope: CoroutineScope,
    private val shouldPoll: () -> Boolean,
    private val onMetrics: (SystemMetrics) -> Unit,
    private val intervalMs: Long = 5000,
) {
    private var metricsJob: Job? = null
    private var connectJob: Job? = null

    val isPolling: Boolean get() = metricsJob?.isActive == true

    /** True from the start of a connect until its REST sync ends: the socket callbacks ignore closes and errors meanwhile. */
    @Volatile var isSwitching = false

    /** True while a connect sequence is still running. */
    val isConnecting: Boolean get() = connectJob?.isActive == true

    /**
     * Runs the connect sequence in the background: [prepare] builds the source (and carries the login over), then the
     * identity answer, one renewal of an expired session, the settings, and finally [ConnectListener.openSocket]. A server
     * that cannot be reached ends the sequence with [ConnectListener.onSyncFailed] and no socket (it would only retry
     * forever in the background). Calling it again cancels the sequence that is still running.
     */
    fun connect(prepare: suspend () -> ConnectSource, listener: ConnectListener) {
        isSwitching = true
        connectJob?.cancel()
        connectJob = scope.launch {
            val source = prepare()
            var synced = false
            try {
                source.hydraInfo()?.let { listener.onInfo(it) }
                listener.onReachable(source.usesTls)
                val settings = try {
                    source.settings()
                } catch (e: HydraApiException) {
                    // An expired token at start-up: renew it once and read again instead of bouncing the user to the login screen.
                    if (e.isSessionProblem && source.renewSession()) source.settings() else throw e
                }
                listener.log("Initial state synchronized via REST")
                listener.onSettings(settings)
                synced = true
                listener.onSynced()
            } catch (e: HydraApiException) {
                listener.log("REST Sync Error: ${e.message}")
                listener.onSyncFailed(e)
            } finally {
                isSwitching = false
            }
            if (synced) listener.openSocket()
        }
    }

    /** Stops the connect sequence that is running, if any (sign-out, the end of the screen). */
    fun cancelConnect() {
        connectJob?.cancel()
        connectJob = null
    }

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
