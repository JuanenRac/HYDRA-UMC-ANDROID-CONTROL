// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - app/src/test/java/com/hydraumc/control/viewmodel/RobotViewModelAccessTest.kt
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.viewmodel

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.hydraumc.control.model.HydraState
import com.hydraumc.control.network.ClaimStanding
import com.hydraumc.control.network.HydraApiClient
import com.hydraumc.control.network.JogLimits
import com.hydraumc.control.network.RobotSafety
import com.hydraumc.control.network.SessionController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Reservations, controller id, safety limits and jog arming, against a real local HTTP server:
 * the controller id goes with every claim, release, jog-arm and command; the refusals of the
 * server read differently; a jog above the limit of the model, or one the server requires to be
 * armed and is not, is never sent.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RobotViewModelAccessTest {

    private lateinit var server: MockWebServer
    private val seen = CopyOnWriteArrayList<RecordedRequest>()
    private val bodies = CopyOnWriteArrayList<String>()

    /** Answers by path; `commandAnswer` is what the command route says. */
    @Volatile private var commandAnswer: () -> MockResponse = { MockResponse().setResponseCode(200).setBody("{}") }
    @Volatile private var claimAnswer: () -> MockResponse = { MockResponse().setResponseCode(200).setBody("""{"success":true}""") }

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                seen += request
                bodies += request.body.clone().readUtf8()
                val path = request.path ?: ""
                return when {
                    path.endsWith("/command") -> commandAnswer()
                    path.endsWith("/claim") || path.endsWith("/release") -> claimAnswer()
                    path.endsWith("/jog-arm") -> MockResponse().setResponseCode(200).setBody("""{"success":true,"armed":true,"ttlMs":60000}""")
                    path.startsWith("/api/claims") -> MockResponse().setResponseCode(200).setBody("""{"claims":[]}""")
                    path.startsWith("/api/safety-limits") -> MockResponse().setResponseCode(404).setBody("{}")
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
        Dispatchers.resetMain()
    }

    private fun newViewModel(): RobotViewModel {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = RobotViewModel(application)
        viewModel.apiClient = HydraApiClient("127.0.0.1", server.port).also { it.authToken = "tok" }
        viewModel.state = HydraState(
            JSONObject("""{"activeControllerId":"c1","controllers":[{"id":"c1","robots":[{"id":1,"playbackState":{"isPlaying":false},"joints":{"j1":0}}]}]}"""),
        )
        viewModel.selectedRobotId.value = 1
        return viewModel
    }

    private fun safety(maxAmount: Double = 20.0, armRequired: Boolean = false) =
        RobotSafety(robotId = 1, jog = JogLimits(maxAmount, 500.0), bedMaxC = 90.0, armRequired = armRequired, armTtlMs = 60_000L, jogArmedUntil = null)

    private fun waitUntil(timeoutMs: Long = 5_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!condition() && System.currentTimeMillis() < deadline) Thread.sleep(20)
        assertTrue("condition not met within ${timeoutMs}ms", condition())
    }

    private fun requestsTo(suffix: String) = seen.withIndex().filter { it.value.path?.endsWith(suffix) == true }

    @Test
    fun `a jog sends the controller id in the body and in the header`() {
        val viewModel = newViewModel()
        viewModel.safetyByRobot.value = mapOf(1 to safety())

        viewModel.jog("robot", "x", 5.0)
        waitUntil { requestsTo("/command").isNotEmpty() }

        val sent = requestsTo("/command").first()
        assertEquals(SessionController.id, sent.value.getHeader("X-Controller-Id"))
        assertEquals(SessionController.id, JSONObject(bodies[sent.index]).getString("controllerId"))
    }

    @Test
    fun `a jog above the limit of the model is not sent`() {
        val viewModel = newViewModel()
        viewModel.safetyByRobot.value = mapOf(1 to safety(maxAmount = 5.0))

        viewModel.jog("robot", "x", 10.0)

        assertNotNull(viewModel.lastError.value)
        assertTrue(viewModel.lastError.value!!, viewModel.lastError.value!!.contains("limit of 5"))
        Thread.sleep(200)
        assertTrue("nothing may reach the server", requestsTo("/command").isEmpty())
    }

    @Test
    fun `a jog the server requires to be armed is not sent until it is armed`() {
        val viewModel = newViewModel()
        viewModel.safetyByRobot.value = mapOf(1 to safety(armRequired = true))
        assertTrue(viewModel.jogNeedsArming(1))

        viewModel.jog("robot", "x", 1.0)
        assertTrue(viewModel.lastError.value!!.contains("not armed"))
        Thread.sleep(200)
        assertTrue(requestsTo("/command").isEmpty())

        viewModel.setJogArmed(true)
        waitUntil { viewModel.jogArmedUntilMs.value[1] != null }
        assertFalse(viewModel.jogNeedsArming(1))
        val arm = requestsTo("/jog-arm").first()
        assertEquals(SessionController.id, JSONObject(bodies[arm.index]).getString("controllerId"))
        assertTrue(JSONObject(bodies[arm.index]).getBoolean("armed"))
    }

    @Test
    fun `a 403 jog-not-armed from the server ends the arming and says so`() {
        commandAnswer = { MockResponse().setResponseCode(403).setBody("""{"error":"not armed","code":"jog-not-armed"}""") }
        val viewModel = newViewModel()
        viewModel.safetyByRobot.value = mapOf(1 to safety())
        viewModel.jogArmedUntilMs.value = mapOf(1 to System.currentTimeMillis() + 60_000)

        viewModel.jog("robot", "x", 1.0)
        waitUntil { viewModel.lastError.value != null }

        assertTrue(viewModel.lastError.value!!.contains("not armed"))
        assertFalse("not read as a missing permission", viewModel.lastError.value!!.contains("Permission"))
        assertNull(viewModel.jogArmedUntilMs.value[1])
    }

    @Test
    fun `the refusals of a reservation read differently`() {
        val viewModel = newViewModel()
        val cases = listOf(
            """{"error":"robot is claimed by bob","code":"other-account"}""" to "another account",
            """{"error":"x","code":"other-controller"}""" to "another device or window",
            """{"error":"x","code":"controller-id-required"}""" to "named none",
        )
        for ((body, expected) in cases) {
            commandAnswer = { MockResponse().setResponseCode(409).setBody(body) }
            viewModel.lastError.value = null
            viewModel.jog("robot", "x", 1.0)
            waitUntil { viewModel.lastError.value != null }
            assertTrue("${viewModel.lastError.value} should contain $expected", viewModel.lastError.value!!.contains(expected))
            Thread.sleep(150)
        }
    }

    @Test
    fun `reserving and releasing send the controller id`() {
        val viewModel = newViewModel()

        viewModel.claimSelectedRobot()
        waitUntil { requestsTo("/claim").isNotEmpty() }
        viewModel.releaseSelectedRobot()
        waitUntil { requestsTo("/release").isNotEmpty() }

        for (suffix in listOf("/claim", "/release")) {
            val sent = requestsTo(suffix).first()
            assertEquals(SessionController.id, sent.value.getHeader("X-Controller-Id"))
            assertEquals(SessionController.id, JSONObject(bodies[sent.index]).getString("controllerId"))
        }
    }

    @Test
    fun `a refused reservation says why`() {
        claimAnswer = { MockResponse().setResponseCode(409).setBody("""{"error":"robot is already claimed by bob","code":"other-account"}""") }
        val viewModel = newViewModel()

        viewModel.claimSelectedRobot()
        waitUntil { viewModel.lastError.value != null }

        assertTrue(viewModel.lastError.value!!.contains("another account"))
    }

    @Test
    fun `the list of reservations tells where the robot stands`() {
        val viewModel = newViewModel()
        viewModel.loginUsername.value = "ana"
        assertEquals(ClaimStanding.FREE, viewModel.claimStandingOf(1))

        viewModel.claims.value = listOf(com.hydraumc.control.network.ClaimInfo(1, "A1", "ana", SessionController.id, 1))
        assertEquals(ClaimStanding.MINE_HERE, viewModel.claimStandingOf(1))
        viewModel.claims.value = listOf(com.hydraumc.control.network.ClaimInfo(1, "A1", "ana", "android-elsewhere", 1))
        assertEquals(ClaimStanding.MINE_ELSEWHERE, viewModel.claimStandingOf(1))
        viewModel.claims.value = listOf(com.hydraumc.control.network.ClaimInfo(1, "A1", "bob", "x", 1))
        assertEquals(ClaimStanding.OTHER, viewModel.claimStandingOf(1))
    }
}
