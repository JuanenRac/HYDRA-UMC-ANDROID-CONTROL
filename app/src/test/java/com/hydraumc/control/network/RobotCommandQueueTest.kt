// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - app/src/test/java/com/hydraumc/control/network/RobotCommandQueueTest.kt
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.network

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The queue of writes of each robot: order, independence between robots, and a stop that drops the jogs that wait. */
@OptIn(ExperimentalCoroutinesApi::class)
class RobotCommandQueueTest {

    @Test
    fun `writes of one robot go out one after the other in the order they were made`() {
        val scope = TestScope(StandardTestDispatcher())
        val queue = RobotCommandQueue()
        val log = mutableListOf<String>()
        val gate = CompletableDeferred<Unit>()

        queue.submit(scope, 1, urgent = false, cancellable = false) { log += "a-start"; gate.await(); log += "a-end" }
        queue.submit(scope, 1, urgent = false, cancellable = false) { log += "b" }
        scope.runCurrent()
        assertEquals(listOf("a-start"), log)
        assertEquals(1, queue.waitingCount(1))

        gate.complete(Unit)
        scope.runCurrent()
        assertEquals(listOf("a-start", "a-end", "b"), log)
        assertEquals(0, queue.waitingCount(1))
    }

    @Test
    fun `a write of another robot does not wait for a slow robot`() {
        val scope = TestScope(StandardTestDispatcher())
        val queue = RobotCommandQueue()
        val log = mutableListOf<String>()
        val gate = CompletableDeferred<Unit>()

        queue.submit(scope, 1, urgent = false, cancellable = false) { gate.await(); log += "slow" }
        queue.submit(scope, 2, urgent = false, cancellable = false) { log += "other" }
        scope.runCurrent()
        assertEquals(listOf("other"), log)

        gate.complete(Unit)
        scope.runCurrent()
        assertEquals(listOf("other", "slow"), log)
    }

    @Test
    fun `a stop skips the line and drops the jogs that wait but not the one on the wire`() {
        val scope = TestScope(StandardTestDispatcher())
        val queue = RobotCommandQueue()
        val log = mutableListOf<String>()
        val dropped = mutableListOf<String>()
        val gate = CompletableDeferred<Unit>()

        queue.submit(scope, 1, urgent = false, cancellable = true, onDropped = { dropped += "on-wire" }) { log += "jog1"; gate.await(); log += "jog1-answered" }
        queue.submit(scope, 1, urgent = false, cancellable = true, onDropped = { dropped += "jog2" }) { log += "jog2" }
        queue.submit(scope, 1, urgent = false, cancellable = false, onDropped = { dropped += "speed" }) { log += "speed" }
        scope.runCurrent()
        assertEquals(listOf("jog1"), log)
        assertEquals(2, queue.waitingCount(1))

        queue.submit(scope, 1, urgent = true, cancellable = false) { log += "stop" }
        scope.runCurrent()
        // The stop went out at once; the second jog was dropped (and told so); the speed change (not a jog) still waits.
        assertEquals(listOf("jog1", "stop"), log)
        assertEquals(listOf("jog2"), dropped)
        assertEquals(1, queue.waitingCount(1))

        gate.complete(Unit)
        scope.runCurrent()
        assertEquals(listOf("jog1", "stop", "jog1-answered", "speed"), log)
        assertEquals(listOf("jog2"), dropped)
    }

    @Test
    fun `a stop of one robot leaves the waiting jogs of another robot alone`() {
        val scope = TestScope(StandardTestDispatcher())
        val queue = RobotCommandQueue()
        val log = mutableListOf<String>()
        val gate = CompletableDeferred<Unit>()

        queue.submit(scope, 2, urgent = false, cancellable = true) { gate.await() }
        queue.submit(scope, 2, urgent = false, cancellable = true) { log += "jog-of-2" }
        scope.runCurrent()
        assertEquals(0, queue.cancelWaitingJogs(1))
        assertEquals(1, queue.waitingCount(2))

        gate.complete(Unit)
        scope.runCurrent()
        assertEquals(listOf("jog-of-2"), log)
    }

    @Test
    fun `ending the session drops everything that waits`() {
        val scope = TestScope(StandardTestDispatcher())
        val queue = RobotCommandQueue()
        val dropped = mutableListOf<Int>()
        val gate = CompletableDeferred<Unit>()

        queue.submit(scope, 1, urgent = false, cancellable = false) { gate.await() }
        queue.submit(scope, 1, urgent = false, cancellable = false, onDropped = { dropped += 1 }) { }
        queue.submit(scope, 1, urgent = false, cancellable = true, onDropped = { dropped += 2 }) { }
        scope.runCurrent()
        queue.cancelAllWaiting()
        scope.runCurrent()
        assertEquals(listOf(1, 2), dropped.sorted())
        assertEquals(0, queue.waitingCount(1))
        assertTrue(gate.isActive)
        gate.complete(Unit)
    }
}
