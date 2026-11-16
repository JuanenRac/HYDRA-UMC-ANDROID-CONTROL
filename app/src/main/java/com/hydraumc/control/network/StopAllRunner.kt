// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - network/StopAllRunner.kt
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** The two calls the global STOP needs from the server (the REST client in the app, a fake in the tests). */
interface StopAllEndpoint {
    suspend fun stopAll(): StopAllResult
    suspend fun stopRobot(robotId: Int, payload: JSONObject, urgent: Boolean, ifMatch: String?): JSONObject
}

/** What the global STOP says to the person when it cannot go on. */
data class StopAllTexts(val sessionExpired: String, val robotNotFound: String)

/**
 * The global STOP, away from the screen state: one call to the server's stop-all route, retried once when nobody answers,
 * with the session renewed once when it expired and the per-robot stop of every known robot when the server rate limits
 * the global call (or does not have the route). Nothing here touches Android, so it is tested on the JVM.
 */
class StopAllRunner(
    private val commands: RobotCommandRepository,
    private val texts: StopAllTexts,
    private val networkRetryPauseMs: Long = 300L,
) {
    /**
     * [relogin] renews the session silently and says whether it worked; [currentEndpoint] is the endpoint to use after a
     * renewal; [onSessionLost] returns the app to the login screen. [knownRobots] gives id and name of every robot known.
     */
    suspend fun run(
        start: StopAllEndpoint,
        knownRobots: () -> List<Pair<Int, String>>,
        relogin: suspend () -> Boolean,
        currentEndpoint: () -> StopAllEndpoint?,
        onSessionLost: () -> Unit,
    ): StopAllStatus {
        var endpoint = start
        var networkRetried = false
        var sessionRenewed = false
        while (true) {
            try {
                return StopAllStatus.Done(endpoint.stopAll())
            } catch (e: HydraApiException) {
                when {
                    // 429: over the server's rate limit. 404: a server that does not have the route yet.
                    e.isRateLimited || e.isNotFound -> return stopRobotsOneByOne(endpoint, knownRobots())
                    e.isSessionProblem && !sessionRenewed -> {
                        sessionRenewed = true
                        if (!relogin()) {
                            onSessionLost()
                            return StopAllStatus.NotConfirmed(texts.sessionExpired)
                        }
                        endpoint = currentEndpoint() ?: return StopAllStatus.NotConfirmed(null)
                    }
                    e.httpCode == null && !networkRetried -> {
                        networkRetried = true
                        pause()
                    }
                    else -> return StopAllStatus.NotConfirmed(e.serverMessage ?: e.message)
                }
            } catch (e: java.io.IOException) {
                if (networkRetried) return StopAllStatus.NotConfirmed(e.message)
                networkRetried = true
                pause()
            } catch (e: Exception) {
                return StopAllStatus.NotConfirmed(e.message)
            }
        }
    }

    // Waited on a real dispatcher, so the pause is real time even where the main dispatcher is a virtual-time test one.
    private suspend fun pause() = withContext(Dispatchers.Default) { delay(networkRetryPauseMs) }

    /** The fallback when the global call is rate limited: the per-robot stop for every known robot. */
    private suspend fun stopRobotsOneByOne(endpoint: StopAllEndpoint, known: List<Pair<Int, String>>): StopAllStatus {
        if (known.isEmpty()) return StopAllStatus.NotConfirmed(texts.robotNotFound)
        val entries = known.map { (id, name) ->
            val error = try {
                commands.postWithRetry({ robotId, body, urgent, ifMatch -> endpoint.stopRobot(robotId, body, urgent, ifMatch) },
                    id, JSONObject().put("command", RobotCommandContract.STOP), urgent = true)
                null
            } catch (e: Exception) {
                (e as? HydraApiException)?.serverMessage ?: e.message ?: "error"
            }
            StopAllRobot(
                robotId = id, controllerId = "", name = name,
                stopped = error == null, valvesOff = false, pumpsOff = false,
                reservedBy = null, error = error,
            )
        }
        val failed = entries.count { !it.stopped }
        return StopAllStatus.Done(
            StopAllResult(
                success = failed == 0, stoppedCount = entries.size - failed, failedCount = failed,
                persisted = true, robots = entries, viaFallback = true,
            ),
        )
    }
}
