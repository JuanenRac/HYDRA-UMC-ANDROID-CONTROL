// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - app/src/test/java/com/hydraumc/control/network/StopAllRunnerTest.kt
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.network

import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

/** The global STOP without the screen: the retry, the renewal of the session and the fallback robot by robot. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StopAllRunnerTest {

    private val texts = StopAllTexts(sessionExpired = "session expired", robotNotFound = "no robot")
    private val result = StopAllResult(success = true, stoppedCount = 2, failedCount = 0, persisted = true, robots = emptyList(), viaFallback = false)

    private class Endpoint(
        val onStopAll: (Int) -> StopAllResult,
        val onStopRobot: (Int) -> JSONObject = { JSONObject().put("success", true) },
    ) : StopAllEndpoint {
        var stopAllCalls = 0
        val robotsStopped = mutableListOf<Int>()
        override suspend fun stopAll(): StopAllResult = onStopAll(++stopAllCalls)
        override suspend fun stopRobot(robotId: Int, payload: JSONObject, urgent: Boolean, ifMatch: String?): JSONObject {
            robotsStopped += robotId
            assertTrue(urgent)
            assertEquals("stop", payload.getString("command"))
            return onStopRobot(robotId)
        }
    }

    private fun runner() = StopAllRunner(RobotCommandRepository(retryPauseMs = 1L), texts, networkRetryPauseMs = 1L)

    private fun run(endpoint: Endpoint, robots: List<Pair<Int, String>> = listOf(1 to "A", 2 to "B"), relogin: Boolean = true, next: StopAllEndpoint? = endpoint, lost: () -> Unit = {}) = runBlocking {
        runner().run(endpoint, { robots }, { relogin }, { next }, lost)
    }

    @Test
    fun theGlobalCallAnswersAtOnce() {
        val endpoint = Endpoint({ result })
        val status = run(endpoint)
        assertEquals(StopAllStatus.Done(result), status)
        assertEquals(1, endpoint.stopAllCalls)
    }

    @Test
    fun aNetworkFailureIsRetriedOnceAndThenGivenUp() {
        val flaky = Endpoint({ n -> if (n == 1) throw IOException("down") else result })
        assertEquals(StopAllStatus.Done(result), run(flaky))
        assertEquals(2, flaky.stopAllCalls)

        val dead = Endpoint({ throw IOException("down") })
        val status = run(dead)
        assertTrue(status is StopAllStatus.NotConfirmed)
        assertEquals(2, dead.stopAllCalls)
    }

    @Test
    fun anExpiredSessionIsRenewedOnceAndAFailedRenewalReturnsToLogin() {
        val expired = Endpoint({ n -> if (n == 1) throw HydraApiException("expired", httpCode = 401) else result })
        assertEquals(StopAllStatus.Done(result), run(expired))

        var lost = false
        val refused = Endpoint({ throw HydraApiException("expired", httpCode = 401) })
        val status = run(refused, relogin = false, lost = { lost = true })
        assertEquals(StopAllStatus.NotConfirmed("session expired"), status)
        assertTrue(lost)
    }

    @Test
    fun aRateLimitedServerGetsOneStopPerRobot() {
        val limited = Endpoint({ throw HydraApiException("slow down", httpCode = 429) })
        val status = run(limited) as StopAllStatus.Done
        assertEquals(listOf(1, 2), limited.robotsStopped)
        assertTrue(status.result.viaFallback)
        assertEquals(2, status.result.stoppedCount)
    }

    @Test
    fun theFallbackReportsTheRobotsThatRefused() {
        val limited = Endpoint({ throw HydraApiException("missing", httpCode = 404) }, { id -> if (id == 2) throw HydraApiException("no", httpCode = 500, serverMessage = "locked") else JSONObject() })
        val status = run(limited) as StopAllStatus.Done
        assertFalse(status.result.success)
        assertEquals(1, status.result.failedCount)
        assertEquals("locked", status.result.robots.first { it.robotId == 2 }.error)
    }

    @Test
    fun theFallbackWithNoKnownRobotIsNotConfirmed() {
        val limited = Endpoint({ throw HydraApiException("slow down", httpCode = 429) })
        assertEquals(StopAllStatus.NotConfirmed("no robot"), run(limited, robots = emptyList()))
    }
}
