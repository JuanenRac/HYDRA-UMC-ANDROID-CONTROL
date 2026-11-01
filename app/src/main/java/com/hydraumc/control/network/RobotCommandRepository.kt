// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - network/RobotCommandRepository.kt
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.network

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** How one write reaches the server (the REST client in the app, a fake in the tests). */
fun interface CommandPoster {
    suspend fun post(robotId: Int, payload: JSONObject, urgent: Boolean, ifMatch: String?): JSONObject
}

/** What the repository measured of one command: how many were sent, how many failed and how long the answers took. */
data class CommandStats(val sent: Int, val failed: Int, val lastMs: Long, val maxMs: Long, val totalMs: Long) {
    val averageMs: Long get() = if (sent - failed > 0) totalMs / (sent - failed) else 0L
}

/**
 * Everything between "the app wants to send this command" and "the server answered": the revision (If-Match) of each robot,
 * the retry of a stop, the `table` command played as jogs on a server that does not know it, the count of jogs still on the
 * wire, the debounce of a command that is sent once the control stops moving, and the time each answer took.
 * It holds no Android class, so it is tested on the JVM against a fake server.
 */
class RobotCommandRepository(
    conditionalMode: () -> ConditionalMode = { ConditionalMode.STANDARD },
    private val nowMs: () -> Long = { System.nanoTime() / 1_000_000L },
    private val retryPauseMs: Long = 250L,
    private val urgentRetries: Int = 2,
) {
    /** The last revision seen of each robot. */
    val revisions = RevisionTracker(conditionalMode)

    /** Whether the server was last seen to understand the `table` command (true until it says otherwise). */
    @Volatile var tableCommandSupported = true

    private val jogsInFlight = HashMap<Int, Int>()
    private val pending = HashMap<String, Job>()
    private val stats = HashMap<String, CommandStats>()

    /** Counts a jog that is going out; call [jogEnded] when it is answered, whatever the answer. */
    @Synchronized
    fun jogStarted(robotId: Int) {
        jogsInFlight[robotId] = (jogsInFlight[robotId] ?: 0) + 1
    }

    @Synchronized
    fun jogEnded(robotId: Int) {
        jogsInFlight[robotId] = ((jogsInFlight[robotId] ?: 1) - 1).coerceAtLeast(0)
    }

    /** True while a jog of [robotId] is still on the wire. */
    @Synchronized
    fun hasJogInFlight(robotId: Int): Boolean = (jogsInFlight[robotId] ?: 0) > 0

    /**
     * Runs [block] after [delayMs], unless a newer call with the same [key] arrives first. A debounced command is
     * keyed by its own name: a pending speed change is replaced only by another speed change, never by a jog or a tap.
     * With [delayMs] of zero the block runs at once (and still replaces what was waiting under the same key).
     */
    fun debounce(scope: CoroutineScope, key: String, delayMs: Long, block: () -> Unit) {
        synchronized(pending) { pending.remove(key)?.cancel() }
        if (delayMs <= 0L) {
            block()
            return
        }
        val job = scope.launch {
            delay(delayMs)
            block()
        }
        synchronized(pending) { pending[key] = job }
    }

    /** Drops what waits under [key] (nothing happens when nothing waits). */
    fun cancelPending(key: String) {
        synchronized(pending) { pending.remove(key)?.cancel() }
    }

    /** Drops every command that waits for its debounce (when the session ends). */
    fun cancelAllPending() {
        synchronized(pending) {
            pending.values.forEach { it.cancel() }
            pending.clear()
        }
    }

    /** The numbers measured for [command], or null when none was sent. */
    @Synchronized
    fun statsOf(command: String): CommandStats? = stats[command]

    /** The numbers of every command sent so far. */
    @Synchronized
    fun allStats(): Map<String, CommandStats> = HashMap(stats)

    @Synchronized
    private fun record(command: String, tookMs: Long, failed: Boolean) {
        val before = stats[command] ?: CommandStats(0, 0, 0, 0, 0)
        stats[command] = if (failed) {
            before.copy(sent = before.sent + 1, failed = before.failed + 1)
        } else {
            before.copy(sent = before.sent + 1, lastMs = tookMs, maxMs = maxOf(before.maxMs, tookMs), totalMs = before.totalMs + tookMs)
        }
    }

    /**
     * Sends [payload] (`{"command": ..., "params": ...}`) to [robotId] with the revision this app last saw of it as If-Match.
     * A `table` move goes out as the jogs that did the same on a server that has no such command. The failure of the
     * server is rethrown for the caller to report.
     */
    suspend fun send(robotId: Int, command: String, payload: JSONObject, poster: CommandPoster): JSONObject {
        val started = nowMs()
        return try {
            val answer = postConditionally(robotId, command, payload, poster)
            record(command, nowMs() - started, failed = false)
            answer
        } catch (e: Throwable) {
            record(command, nowMs() - started, failed = true)
            throw e
        }
    }

    /**
     * A stop is worth retrying: it is posted with short timeouts, and a network failure (no answer at all) is tried again
     * up to [urgentRetries] times in about a second. An answer from the server, of any kind, is final.
     */
    suspend fun postWithRetry(poster: CommandPoster, robotId: Int, payload: JSONObject, urgent: Boolean, ifMatch: String? = null): JSONObject {
        var attempt = 0
        while (true) {
            try {
                return poster.post(robotId, payload, urgent, ifMatch)
            } catch (e: HydraApiException) {
                if (!urgent || e.httpCode != null || attempt >= urgentRetries) throw e
            } catch (e: java.io.IOException) {
                if (!urgent || attempt >= urgentRetries) throw e
            }
            attempt++
            // Waited on a real dispatcher (not the caller's), so the pause is real time even where the main dispatcher is a virtual-time test one.
            withContext(Dispatchers.Default) { delay(retryPauseMs) }
        }
    }

    /** One write with the revision as If-Match (never for STOP, and only when the mode and the tracker allow it); the answer's revision is kept for the next write, and a 412 teaches the one the server is at now. */
    suspend fun withRevision(robotId: Int, command: String, call: suspend (String?) -> JSONObject): JSONObject {
        val ticket = revisions.begin(robotId, command)
        try {
            val answer = call(ticket.ifMatch)
            revisions.finish(robotId, ticket.seq, revisionOf(answer))
            return answer
        } catch (e: HydraApiException) {
            revisions.finish(robotId, ticket.seq, null)
            if (e.isRevisionMismatch) revisions.learn(robotId, e.revision)
            throw e
        } catch (e: Exception) {
            revisions.finish(robotId, ticket.seq, null)
            throw e
        }
    }

    private suspend fun postConditionally(robotId: Int, command: String, payload: JSONObject, poster: CommandPoster): JSONObject {
        val params = payload.optJSONObject("params")
        if (command == RobotCommandContract.TABLE && !tableCommandSupported && params != null) return postTableAsJogs(poster, robotId, params)
        return try {
            withRevision(robotId, command) { ifMatch ->
                postWithRetry(poster, robotId, payload, urgent = command == RobotCommandContract.STOP, ifMatch = ifMatch)
            }
        } catch (e: HydraApiException) {
            if (command == RobotCommandContract.TABLE && params != null && isUnknownCommand(e)) {
                tableCommandSupported = false
                postTableAsJogs(poster, robotId, params)
            } else {
                throw e
            }
        }
    }

    private suspend fun postTableAsJogs(poster: CommandPoster, robotId: Int, params: JSONObject): JSONObject {
        var last = JSONObject().put("success", true)
        for (jog in tableAsJogs(params)) {
            last = postWithRetry(poster, robotId, JSONObject().put("command", RobotCommandContract.JOG).put("params", jog), urgent = false)
        }
        return last
    }

    companion object {
        /** The text of the warnings of an answer the server accepted but applied nothing of, or null when there are none. */
        fun warningsOf(answer: JSONObject): String? {
            val warnings = answer.optJSONArray("warnings") ?: return null
            if (warnings.length() == 0) return null
            return (0 until warnings.length()).joinToString("; ") { i ->
                val item = warnings.opt(i)
                if (item is JSONObject) item.optString("message", item.toString()) else item.toString()
            }
        }
    }
}
