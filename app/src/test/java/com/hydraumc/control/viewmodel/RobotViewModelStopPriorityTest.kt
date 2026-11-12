// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - app/src/test/java/com/hydraumc/control/viewmodel/RobotViewModelStopPriorityTest.kt
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.viewmodel

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.hydraumc.control.MainDispatcherSwap
import com.hydraumc.control.model.HydraState
import com.hydraumc.control.network.HydraApiClient
import com.hydraumc.control.network.RobotCommandContract
import kotlinx.coroutines.ExperimentalCoroutinesApi
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
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
import java.util.concurrent.TimeUnit

/**
 * The order of the writes of one phone against a real local HTTP server: a stop is never held back by a jog still on the
 * wire (and a second jog is), the disable/enable of a robot goes back when the server refuses it, and the timings of the
 * answers are kept.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RobotViewModelStopPriorityTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        MainDispatcherSwap.set()
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        try {
            if (::server.isInitialized) server.shutdown()
        } finally {
            MainDispatcherSwap.reset()
        }
    }

    private fun newViewModel(enabled: Boolean = true): RobotViewModel {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = RobotViewModel(application)
        viewModel.apiClient = HydraApiClient("127.0.0.1", server.port).also { it.authToken = "tok" }
        viewModel.state = HydraState(
            JSONObject(
                """{"activeControllerId":"c1","controllers":[{"id":"c1","robots":[
                    {"id":1,"name":"A","enabled":$enabled,"pos":{"x":0,"y":0,"z":0},"playbackState":{"isPlaying":false}}]}]}""",
            ),
        )
        viewModel.selectedRobotId.value = 1
        return viewModel
    }

    private fun waitUntil(timeoutMs: Long = 8_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!condition() && System.currentTimeMillis() < deadline) Thread.sleep(20)
        assertTrue("condition not met within ${timeoutMs}ms", condition())
    }

    @Test
    fun `a stop goes out while a jog is still on the wire and a second jog does not`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}").setBodyDelay(8000, TimeUnit.MILLISECONDS)) // long enough that a slow runner cannot answer the first jog before the checks below end
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        val viewModel = newViewModel()

        viewModel.jog("robot", "x", 1.0)
        waitUntil { viewModel.commands.hasJogInFlight(1) }
        viewModel.jog("robot", "x", 1.0) // held back: the first jog is not answered yet
        viewModel.sendCommand("stop")

        val first = server.takeRequest(5, TimeUnit.SECONDS)
        val second = server.takeRequest(5, TimeUnit.SECONDS)
        // The jog counts as on the wire as soon as it is handed to the network, but its request and the stop travel on two connections, so which of
        // the two the server sees first depends on the speed of the runner: what matters is that both arrive and that the second jog does not.
        val commands = listOf(first!!, second!!).map { JSONObject(it.body.readUtf8()).getString("command") }
        assertEquals(listOf("jog", "stop"), commands.sorted())
        assertNull("no third request: the second jog was held back", server.takeRequest(300, TimeUnit.MILLISECONDS))
    }

    @Test
    fun `the number of a stop is above the number of the jog sent before it`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        val viewModel = newViewModel()

        viewModel.jog("robot", "x", 1.0)
        waitUntil { server.requestCount >= 1 && !viewModel.commands.hasJogInFlight(1) }
        viewModel.sendCommand("stop")

        val jog = JSONObject(server.takeRequest(5, TimeUnit.SECONDS)!!.body.readUtf8())
        val stop = JSONObject(server.takeRequest(5, TimeUnit.SECONDS)!!.body.readUtf8())
        assertTrue(stop.getLong("seq") > jog.getLong("seq"))
    }

    @Test
    fun `disabling a robot goes back when the server refuses it`() {
        server.enqueue(MockResponse().setResponseCode(500).setBody("error"))
        val viewModel = newViewModel(enabled = true)

        viewModel.sendCommand("disable")
        assertFalse("optimistic: shown disabled at once", viewModel.state.robotById(1)!!.isEnabled)
        waitUntil { viewModel.lastError.value != null }

        assertTrue("rolled back to enabled", viewModel.state.robotById(1)!!.isEnabled)
    }

    @Test
    fun `enabling a robot goes back to disabled when the server answers 403`() {
        server.enqueue(MockResponse().setResponseCode(403).setBody("""{"error":"administrator only"}"""))
        val viewModel = newViewModel(enabled = false)

        viewModel.sendCommand("enable")
        assertTrue(viewModel.state.robotById(1)!!.isEnabled)
        waitUntil { viewModel.lastError.value != null }

        assertFalse(viewModel.state.robotById(1)!!.isEnabled)
    }

    @Test
    fun `the time of an answered command is measured`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        val viewModel = newViewModel()

        viewModel.sendCommand("play")
        waitUntil { viewModel.commands.statsOf(RobotCommandContract.PLAY) != null }

        val stats = viewModel.commands.statsOf(RobotCommandContract.PLAY)!!
        assertEquals(1, stats.sent)
        assertEquals(0, stats.failed)
        assertNotNull(viewModel.commands.allStats()[RobotCommandContract.PLAY])
    }
}
