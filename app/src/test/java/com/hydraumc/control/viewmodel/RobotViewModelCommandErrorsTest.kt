// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - app/src/test/java/com/hydraumc/control/viewmodel/RobotViewModelCommandErrorsTest.kt
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
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
import okhttp3.mockwebserver.SocketPolicy
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
 * How a command the server refuses (or only half accepts) reaches the
 * operator, and the guards around jogging and stopping, against a real local
 * HTTP server ([MockWebServer]):
 *  - 400 (an unknown command), 409 (a robot reserved by another account) and a
 *    role 403 each read differently, and the optimistic change is rolled back;
 *  - a 200 that carries `warnings` means nothing was applied: rolled back too;
 *  - while a jog is on the wire the next tick is dropped, and nothing jogs
 *    while a trajectory is running;
 *  - a STOP that gets no answer at all is sent again; one the server answered
 *    is not.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RobotViewModelCommandErrorsTest {

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

    private fun newViewModel(playing: Boolean = false): RobotViewModel {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = RobotViewModel(application)
        viewModel.apiClient = HydraApiClient("127.0.0.1", server.port)
        viewModel.state = HydraState(
            JSONObject(
                """{"activeControllerId":"c1","controllers":[{"id":"c1","robots":[
                    {"id":1,"playbackState":{"isPlaying":$playing},"joints":{"j1":0}}]}]}""",
            ),
        )
        viewModel.selectedRobotId.value = 1
        return viewModel
    }

    private fun waitUntil(timeoutMs: Long = 5_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!condition() && System.currentTimeMillis() < deadline) Thread.sleep(20)
        assertTrue("condition not met within ${timeoutMs}ms", condition())
    }

    @Test
    fun `a 409 says the robot is reserved by another account and rolls the change back`() {
        server.enqueue(MockResponse().setResponseCode(409).setBody("""{"error":"robot 1 is reserved by alice"}"""))
        val viewModel = newViewModel()

        viewModel.sendCommand("play")
        waitUntil { viewModel.lastError.value != null }

        assertTrue(viewModel.lastError.value!!.contains("reserved by another account"))
        assertTrue(viewModel.lastError.value!!.contains("alice"))
        assertFalse(viewModel.state.robotById(1)!!.isPlaying)
    }

    @Test
    fun `a 400 shows the server's reason and rolls the change back`() {
        server.enqueue(MockResponse().setResponseCode(400).setBody("""{"error":"unknown command"}"""))
        val viewModel = newViewModel()

        viewModel.sendCommand("play")
        waitUntil { viewModel.lastError.value != null }

        assertTrue(viewModel.lastError.value!!.contains("unknown command"))
        assertFalse(viewModel.state.robotById(1)!!.isPlaying)
    }

    @Test
    fun `a role 403 reads as a missing permission`() {
        server.enqueue(MockResponse().setResponseCode(403).setBody("""{"error":"Access denied: admin privileges required"}"""))
        val viewModel = newViewModel()

        viewModel.sendCommand("play")
        waitUntil { viewModel.lastError.value != null }

        assertTrue(viewModel.lastError.value!!.contains("Permission denied"))
        assertFalse(viewModel.state.robotById(1)!!.isPlaying)
    }

    @Test
    fun `a 200 answer with warnings counts as not applied`() {
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setBody("""{"success":true,"warnings":[{"message":"speed out of range - not applied"}]}"""),
        )
        val viewModel = newViewModel()

        viewModel.sendCommand("play")
        waitUntil { viewModel.lastError.value != null }

        assertTrue(viewModel.lastError.value!!.contains("speed out of range"))
        assertFalse("the optimistic change must be rolled back", viewModel.state.robotById(1)!!.isPlaying)
    }

    @Test
    fun `enable and disable are no longer offered`() {
        val viewModel = newViewModel()

        viewModel.sendCommand("disable")

        assertEquals("nothing is sent for a command the server does not have", 0, server.requestCount)
        assertTrue(viewModel.lastError.value!!.contains("Unknown command"))
    }

    @Test
    fun `while one jog is on the wire the next tick is dropped`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}").setBodyDelay(800, TimeUnit.MILLISECONDS))
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        val viewModel = newViewModel()

        viewModel.jogJ1(direction = 1, jogStep = 1.0)
        viewModel.jogJ1(direction = 1, jogStep = 1.0) // the first is still in flight: dropped
        viewModel.jogJ1(direction = 1, jogStep = 1.0)

        assertEquals(1.0, viewModel.state.robotById(1)!!.joints.optDouble("j1", 0.0), 0.001)
        waitUntil { server.requestCount == 1 }

        // Once the first has been answered, the next tick goes through again.
        Thread.sleep(1_200)
        viewModel.jogJ1(direction = 1, jogStep = 1.0)
        waitUntil { server.requestCount == 2 }
    }

    @Test
    fun `nothing jogs while a trajectory is running`() {
        val viewModel = newViewModel(playing = true)

        viewModel.jogJ1(direction = 1, jogStep = 5.0)
        viewModel.jogXYZ("robot", 1, 0, 0, 5.0)

        assertEquals(0, server.requestCount)
        assertEquals(0.0, viewModel.state.robotById(1)!!.joints.optDouble("j1", 0.0), 0.001)
    }

    @Test
    fun `a stop survives two dropped connections`() {
        // The connection is cut after the request was read, twice; the third attempt is answered.
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        val viewModel = newViewModel(playing = true)

        viewModel.sendCommand("stop")

        waitUntil(8_000) { server.requestCount >= 3 }
        Thread.sleep(500)
        assertTrue("the answered attempt means no error is shown", viewModel.lastError.value == null)
        assertFalse("the stop stays applied", viewModel.state.robotById(1)!!.isPlaying)
    }

    @Test
    fun `a stop the server refused is not retried`() {
        server.enqueue(MockResponse().setResponseCode(409).setBody("""{"error":"reserved"}"""))
        val viewModel = newViewModel(playing = true)

        viewModel.sendCommand("stop")
        waitUntil { viewModel.lastError.value != null }
        Thread.sleep(700)

        assertEquals(1, server.requestCount)
    }
}
