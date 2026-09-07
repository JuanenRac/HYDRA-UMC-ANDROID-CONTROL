// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - app/src/test/java/com/hydraumc/control/viewmodel/RobotViewModelSendAtomicCommandTest.kt
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0 - see LICENSE
// =============================================================================
package com.hydraumc.control.viewmodel

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.hydraumc.control.model.HydraState
import com.hydraumc.control.network.HydraApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

/**
 * Found in an ecosystem-wide software-improvements audit:
 * RobotViewModel.sendAtomicCommand()'s optimistic-mutate-then-rollback-on-
 * failure flow and its combinedWith fan-out have no test at all, unlike
 * other parts of this app (kinematics, state parsing, updates) that
 * already do. Real coverage against a real (if local) HTTP server
 * ([MockWebServer]) - never a mocked [HydraApiClient] - exercised via
 * `sendCommand("play")`, a real, server-supported command a real screen's
 * button actually wires to, the same convention HYDRA-UMC-DSI's own
 * robot_view_model_test.dart already uses for its own (Flutter) port of
 * this exact optimistic-mutate/rollback machinery.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RobotViewModelSendAtomicCommandTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
        Dispatchers.resetMain()
    }

    /** Two real robots, robot 1 combinedWith robot 2, both initially idle. */
    private fun rawStateWith(robot1Playing: Boolean, robot2Playing: Boolean): HydraState {
        val json = """
            {
              "activeControllerId": "c1",
              "controllers": [
                {
                  "id": "c1",
                  "robots": [
                    {"id": 1, "playbackState": {"isPlaying": $robot1Playing}, "combinedWith": [2]},
                    {"id": 2, "playbackState": {"isPlaying": $robot2Playing}, "combinedWith": [1]}
                  ]
                }
              ]
            }
        """.trimIndent()
        return HydraState(JSONObject(json))
    }

    private fun newViewModel(): RobotViewModel {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = RobotViewModel(application)
        viewModel.apiClient = HydraApiClient("127.0.0.1", server.port)
        viewModel.state = rawStateWith(robot1Playing = false, robot2Playing = false)
        viewModel.selectedRobotId.value = 1
        return viewModel
    }

    /** Polls a real, observable side effect instead of racing a background
     * coroutine dispatched onto a real Dispatchers.IO thread by
     * HydraApiClient's own withContext(Dispatchers.IO) - the request
     * really leaves this process over a real loopback socket. */
    private fun waitUntil(timeoutMs: Long = 5_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!condition() && System.currentTimeMillis() < deadline) {
            Thread.sleep(20)
        }
        assertTrue("condition not met within ${timeoutMs}ms", condition())
    }

    @Test
    fun `play mutates both the target robot and its combinedWith sibling optimistically`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        val viewModel = newViewModel()

        viewModel.sendCommand("play")

        // Optimistic: applied to the UI state synchronously, before the
        // network round-trip even starts.
        assertTrue(viewModel.state.robotById(1)!!.isPlaying)
        assertTrue(viewModel.state.robotById(2)!!.isPlaying)
    }

    @Test
    fun `play sends the real POST to the real per-robot command endpoint`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        val viewModel = newViewModel()

        viewModel.sendCommand("play")

        val request = server.takeRequest(5, TimeUnit.SECONDS)
        assertEquals("POST", request?.method)
        assertEquals("/api/robot/1/command", request?.path)
        val body = JSONObject(request!!.body.readUtf8())
        assertEquals("play", body.getString("command"))
    }

    @Test
    fun `a successful send clears lastError and never rolls back`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        val viewModel = newViewModel()

        viewModel.sendCommand("play")
        waitUntil { viewModel.lastError.value == null && server.requestCount >= 1 }

        assertTrue(viewModel.state.robotById(1)!!.isPlaying)
        assertTrue(viewModel.state.robotById(2)!!.isPlaying)
    }

    @Test
    fun `a failed send rolls back both the target robot and its combinedWith sibling`() {
        server.enqueue(MockResponse().setResponseCode(500).setBody("server error"))
        val viewModel = newViewModel()

        viewModel.sendCommand("play")

        // Real rollback happens asynchronously, after the real failed
        // response comes back - poll for the real, observable outcome
        // instead of a fixed sleep.
        waitUntil { viewModel.lastError.value != null }

        assertFalse("robot 1 must be rolled back to not-playing", viewModel.state.robotById(1)!!.isPlaying)
        assertFalse("robot 2 (combinedWith) must be rolled back too", viewModel.state.robotById(2)!!.isPlaying)
    }

    @Test
    fun `a non-combined command like jogJ1 never propagates to the combinedWith sibling`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        val viewModel = newViewModel()

        viewModel.jogJ1(direction = 1, jogStep = 5.0)

        // Real, observable mutation on the target robot only.
        assertEquals(5.0, viewModel.state.robotById(1)!!.joints.optDouble("j1", 0.0), 0.001)
        // jog is per-axis/per-slot - it only ever makes sense for the one
        // robot being controlled, never propagated to combinedWith. Robot
        // 2's own j1 must stay at its real default - `joints` itself may
        // get lazily created as a side effect of unrelated Compose-state
        // derivation reading every robot's own `.joints` getter, so the
        // real, meaningful check is the VALUE, not whether that key exists.
        assertEquals(0.0, viewModel.state.robotById(2)!!.joints.optDouble("j1", 0.0), 0.001)

        val request = server.takeRequest(5, TimeUnit.SECONDS)
        assertEquals("/api/robot/1/command", request?.path)
    }
}
