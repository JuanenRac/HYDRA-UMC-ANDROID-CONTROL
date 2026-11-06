// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - network/RobotCommandQueue.kt
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.network

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * One queue per robot for the writes the app sends: the writes of a robot go out one after the other, in the order they were
 * made (two taps never overtake each other on the wire), and the writes of different robots never wait for each other.
 * A STOP (and the commands that switch a robot off) never waits: it goes out at once, and it cancels the jogs of that robot
 * that are still waiting for their turn, so a held direction does not keep moving the robot after the stop. A write
 * that already left is never cancelled (it is on the wire; the server answers it).
 * It holds no Android class, so it is tested on the JVM.
 */
class RobotCommandQueue {
    private class Entry(val cancellable: Boolean) {
        var job: Job? = null
        @Volatile var started = false
        val waiting: Boolean get() = !started && job?.isActive == true
    }

    private val locks = HashMap<Int, Mutex>()
    private val entries = HashMap<Int, MutableList<Entry>>()

    @Synchronized
    private fun lockOf(robotId: Int): Mutex = locks.getOrPut(robotId) { Mutex() }

    /** How many writes of [robotId] wait for their turn (not counting the one on the wire). */
    @Synchronized
    fun waitingCount(robotId: Int): Int = entries[robotId]?.count { it.waiting } ?: 0

    /**
     * Queues [block] behind what [robotId] already sends. An [urgent] write skips the queue and cancels the [cancellable]
     * writes (the jogs) that wait. Returns the job of this write. [onDropped] runs (on any thread) when the write was
     * cancelled before its turn came, so what was counted or changed for it can be undone.
     */
    fun submit(scope: CoroutineScope, robotId: Int, urgent: Boolean, cancellable: Boolean, onDropped: () -> Unit = {}, block: suspend () -> Unit): Job {
        if (urgent) {
            cancelWaitingJogs(robotId)
            return scope.launch { block() }
        }
        val lock = lockOf(robotId)
        val entry = Entry(cancellable)
        // Listed before it can run, so a stop that arrives right now already sees it.
        val job = scope.launch(start = CoroutineStart.LAZY) {
            lock.withLock {
                entry.started = true
                block()
            }
        }
        synchronized(this) {
            entry.job = job
            val list = entries.getOrPut(robotId) { ArrayList() }
            list.removeAll { it.job?.isActive != true }
            list.add(entry)
        }
        job.invokeOnCompletion { cause ->
            if (cause != null && !entry.started) onDropped()
        }
        job.start()
        return job
    }

    /** Cancels the jogs of [robotId] that wait for their turn; returns how many. The one already on the wire is left alone. */
    fun cancelWaitingJogs(robotId: Int): Int {
        val toCancel = synchronized(this) { entries[robotId]?.filter { it.cancellable && it.waiting } ?: emptyList() }
        toCancel.forEach { it.job?.cancel() }
        return toCancel.size
    }

    /** Cancels every write that still waits for its turn (the session ends). */
    fun cancelAllWaiting() {
        val toCancel = synchronized(this) { entries.values.flatten().filter { it.waiting } }
        toCancel.forEach { it.job?.cancel() }
    }
}
