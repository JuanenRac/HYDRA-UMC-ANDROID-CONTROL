// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - network/DeadManJogRunner.kt
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.network

import com.hydraumc.control.util.DeadManJog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

/**
 * Holds a continuous jog: `jogStart`, then a `jogHeartbeat` every heartbeat period while the control is held, then
 * `jogStop`. The axis must never keep moving on its own, so the runner ends the session by itself, with a stop
 * sent as urgently as a STOP, as soon as the [DeadManJog.Watchdog] stops trusting the link, the hold is too long,
 * the link health blocks it, the server refuses a heartbeat, or [interrupt] is called (the screen was left, a STOP
 * was sent). The server does the same on its side when no heartbeat arrives for the time of the contract.
 *
 * It holds no Android class and sends through a [CommandPoster], so it is tested on the JVM with a fake server
 * and the virtual clock of the coroutine tests. All the numbers are the [DeadManJog.Settings].
 */
class DeadManJogRunner(
    private val scope: CoroutineScope,
    private val poster: CommandPoster,
    private val settings: DeadManJog.Settings = DeadManJog.Settings(),
    private val clock: () -> Long = { System.nanoTime() / 1_000_000L },
    /** True while the link health lets the jog go on (checked at every heartbeat). */
    private val linkAllows: () -> Boolean = { true },
    /** A heartbeat was answered after [Long] ms: the link health takes it as a sample. */
    private val onRoundTrip: (Long) -> Unit = {},
    /** A session ended; [stopConfirmed] tells whether the server answered the stop (if not, its own timeout will stop the axis). */
    private val onEnded: (robotId: Int, reason: DeadManJog.EndReason, stopConfirmed: Boolean) -> Unit = { _, _, _ -> },
) {
    private class Session(val robotId: Int, val id: String, val job: Job) {
        @Volatile var ending = false
    }

    private val sessions = HashMap<Int, Session>()
    private var counter = 0L

    @Synchronized
    fun isRunning(robotId: Int): Boolean = sessions[robotId]?.ending == false

    @Synchronized
    fun anyRunning(): Boolean = sessions.values.any { !it.ending }

    private fun payload(command: String, params: JSONObject): JSONObject = JSONObject().put("command", command).put("params", params)

    /**
     * Sends `jogStart` and, when the server accepts it, begins the heartbeat. The failure of the start is thrown to the
     * caller (nothing moves then); a start that was answered but not understood is the caller's to explain.
     */
    suspend fun start(robotId: Int, target: String, axis: String, velocity: Double) {
        if (isRunning(robotId)) return
        val sessionId = synchronized(this) { "dm-${++counter}-${clock()}" }
        val velocityClamped = DeadManJog.clampVelocity(velocity, settings)
        val params = JSONObject()
            .put("sessionId", sessionId).put("target", target).put("axis", axis)
            .put("velocity", velocityClamped).put("heartbeatMs", settings.heartbeatMs).put("timeoutMs", settings.serverTimeoutMs)
        withTimeout(settings.serverTimeoutMs * 5) { poster.post(robotId, payload(RobotCommandContract.JOG_START, params), true, null) }
        val watchdog = DeadManJog.Watchdog(settings, clock)
        synchronized(this) {
            sessions[robotId] = Session(robotId, sessionId, scope.launch { heartbeats(robotId, sessionId, watchdog) })
        }
    }

    /** The control was released. */
    fun release(robotId: Int) = end(robotId, DeadManJog.EndReason.RELEASED)

    /** The screen was left, the connection dropped or a STOP is going out: every session ends and its axis is stopped. */
    fun interrupt() {
        val ids = synchronized(this) { sessions.values.filter { !it.ending }.map { it.robotId } }
        ids.forEach { end(it, DeadManJog.EndReason.INTERRUPTED) }
    }

    private fun end(robotId: Int, reason: DeadManJog.EndReason) {
        val session = synchronized(this) {
            val s = sessions[robotId] ?: return
            if (s.ending) return
            s.ending = true
            s
        }
        // The stop is sent from a coroutine of its own: the heartbeat loop may be the one that called this, and it is cancelled.
        scope.launch {
            session.job.cancel()
            val confirmed = sendStop(robotId, session.id)
            synchronized(this@DeadManJogRunner) { if (sessions[robotId] === session) sessions.remove(robotId) }
            onEnded(robotId, reason, confirmed)
        }
    }

    private suspend fun sendStop(robotId: Int, sessionId: String): Boolean {
        val stop = payload(RobotCommandContract.JOG_STOP, JSONObject().put("sessionId", sessionId))
        repeat(settings.stopAttempts) { attempt ->
            try {
                withTimeout(settings.stopTimeoutMs) { poster.post(robotId, JSONObject(stop.toString()), true, null) }
                return true
            } catch (e: CancellationException) {
                if (e is kotlinx.coroutines.TimeoutCancellationException) Unit else throw e
            } catch (_: Exception) {
                // tried again below
            }
            if (attempt < settings.stopAttempts - 1) delay(settings.stopRetryPauseMs)
        }
        return false
    }

    private suspend fun heartbeats(robotId: Int, sessionId: String, watchdog: DeadManJog.Watchdog) {
        var sequence = 0L
        while (true) {
            val startedAt = clock()
            val params = JSONObject().put("sessionId", sessionId).put("sequence", ++sequence)
            var refused = false
            try {
                withTimeout(settings.serverTimeoutMs - settings.heartbeatMs) { poster.post(robotId, payload(RobotCommandContract.JOG_HEARTBEAT, params), true, null) }
                watchdog.acked()
                onRoundTrip(clock() - startedAt)
            } catch (e: HydraApiException) {
                // An answer of the server that is an error: the session is not (or no longer) accepted there.
                if (e.httpCode != null && e.httpCode in 400..499 && e.httpCode != 408 && e.httpCode != 429) refused = true else watchdog.missed()
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                watchdog.missed()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                watchdog.missed()
            }
            val verdict = when {
                refused -> DeadManJog.EndReason.SERVER_REFUSED
                else -> watchdog.verdict() ?: if (!linkAllows()) DeadManJog.EndReason.BAD_LINK else null
            }
            if (verdict != null) {
                end(robotId, verdict)
                return
            }
            delay((settings.heartbeatMs - (clock() - startedAt)).coerceAtLeast(1L))
        }
    }
}
