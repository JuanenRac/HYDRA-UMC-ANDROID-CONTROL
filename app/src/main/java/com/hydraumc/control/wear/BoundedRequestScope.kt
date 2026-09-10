// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - Bounded per-requestId coroutine launcher
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0 - see LICENSE
//
// Found while auditing the code (ANDROID-01):
// WatchVoiceRelayService's own CoroutineScope(SupervisorJob() + Dispatchers.IO)
// was never cancelled in onDestroy(), and every onMessageReceived() call
// launched a new, completely unbounded coroutine - no limit on how many
// could be in flight at once, and no defense against a duplicate/retried
// Data Layer message for the same requestId piling up a second in-flight
// attempt (and, eventually, a second reply) alongside the first. A real
// service recreation (Android can and does recreate a Service instance)
// left every already-launched coroutine running to completion regardless,
// which is exactly the "trabajo retenido/duplicado durante recreaciones"
// risk noted while auditing the code.
//
// This is deliberately extracted into its own plain class with no
// Android/GMS dependency at all: WatchVoiceRelayService's own onDestroy()
// still needs Robolectric to test for real (a real Service lifecycle), but
// the actual concurrency-bounding logic below needs none of that and is
// fully covered by BoundedRequestScopeTest.kt using kotlinx-coroutines-test
// directly - no mocking library needed either.
// =============================================================================
package com.hydraumc.control.wear

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Launches [block] on [scope], keyed by [requestId]: a new launch for a
 * requestId already in flight cancels the previous one first, and a
 * completed/cancelled launch removes itself from the tracked set - so this
 * never grows without bound and never runs two attempts for the same
 * requestId at once. [cancelAll] cancels every currently tracked job (call
 * it from the owning component's own onDestroy()/equivalent teardown).
 */
class BoundedRequestScope(private val scope: CoroutineScope) {
    private val active = ConcurrentHashMap<String, Job>()

    /** Currently in-flight request count - test-visible, not used by production logic. */
    val activeCount: Int
        get() = active.size

    fun launch(requestId: String, block: suspend CoroutineScope.() -> Unit) {
        active[requestId]?.cancel()
        val job = scope.launch(block = block)
        active[requestId] = job
        job.invokeOnCompletion { active.remove(requestId, job) }
    }

    fun cancelAll() {
        active.values.forEach { it.cancel() }
        active.clear()
    }
}
