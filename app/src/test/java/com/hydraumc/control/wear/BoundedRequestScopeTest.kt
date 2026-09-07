// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - BoundedRequestScope unit tests
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0 - see LICENSE
//
// Real regression coverage for ANDROID-01 (found in an ecosystem-wide
// software-improvements audit): WatchVoiceRelayService's own coroutine
// scope used to have no per-requestId concurrency bound and was never
// cancelled on destroy. Plain kotlinx-coroutines-test, no Android/GMS
// dependency and no mocking library needed - this class has none.
// =============================================================================
@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.hydraumc.control.wear

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BoundedRequestScopeTest {
    @Test
    fun `two different requestIds both run to completion`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = BoundedRequestScope(CoroutineScope(dispatcher))
        val done = mutableListOf<String>()

        scope.launch("a") { done += "a" }
        scope.launch("b") { done += "b" }
        advanceUntilIdle()

        assertEquals(listOf("a", "b"), done)
        assertEquals(0, scope.activeCount) // both completed and removed themselves
    }

    @Test
    fun `a new launch for an already in-flight requestId cancels the previous one`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = BoundedRequestScope(CoroutineScope(dispatcher))
        var firstReachedPastCancellationPoint = false
        var secondCompleted = false

        scope.launch("dup") {
            awaitCancellation() // suspends forever unless this job is cancelled
            firstReachedPastCancellationPoint = true // never reached if cancelled, as expected here
        }
        // Immediately supersede it with a second launch for the SAME
        // requestId, before the scheduler has even started the first body.
        scope.launch("dup") { secondCompleted = true }
        advanceUntilIdle()

        assertFalse("the superseded first attempt must never run past its cancellation point", firstReachedPastCancellationPoint)
        assertTrue("the second attempt (the one that superseded it) must run", secondCompleted)
        assertEquals(0, scope.activeCount)
    }

    @Test
    fun `cancelAll stops every still-running request and clears tracking`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = BoundedRequestScope(CoroutineScope(dispatcher))
        var reachedPastCancellationPoint = false

        scope.launch("long-running") {
            awaitCancellation()
            reachedPastCancellationPoint = true
        }
        // Let the coroutine actually start (suspend at awaitCancellation)
        // before tearing everything down - this is the real onDestroy()
        // path: a request still in flight when the component is destroyed.
        advanceUntilIdle()
        assertEquals(1, scope.activeCount)

        scope.cancelAll()
        advanceUntilIdle()

        assertEquals(0, scope.activeCount)
        assertFalse(reachedPastCancellationPoint)
    }

    @Test
    fun `activeCount reflects only genuinely in-flight requests`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = BoundedRequestScope(CoroutineScope(dispatcher))

        assertEquals(0, scope.activeCount)
        scope.launch("x") { awaitCancellation() }
        advanceUntilIdle()
        assertEquals(1, scope.activeCount)
        scope.cancelAll()
        assertEquals(0, scope.activeCount)
    }
}
