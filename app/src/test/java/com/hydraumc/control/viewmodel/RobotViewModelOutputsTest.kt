// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - app/src/test/java/com/hydraumc/control/viewmodel/RobotViewModelOutputsTest.kt
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.viewmodel

import com.hydraumc.control.EmptyAuthStore
import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.hydraumc.control.MainDispatcherSwap
import com.hydraumc.control.model.HydraState
import com.hydraumc.control.network.HydraApiClient
import com.hydraumc.control.network.ListenOnly
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
 * The valves, the pumps, the jog step and the vision switch through the view model, against a loopback [MockWebServer]: what goes out
 * (the command, its index and state), what shows at once (the optimistic change) and what is refused (Listen only, no robot, no client).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RobotViewModelOutputsTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        MainDispatcherSwap.set()
        ListenOnly.enabled = false
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        try {
            if (::server.isInitialized) server.shutdown()
        } finally {
            ListenOnly.enabled = false
            MainDispatcherSwap.reset()
        }
    }

    private fun newViewModel(withClient: Boolean = true, selected: Boolean = true): RobotViewModel {
        val json = """
            {"activeControllerId":"c1","controllers":[{"id":"c1","robots":[
              {"id":1,"name":"R1","online":true,"enabled":true,"valves":[false,false],"pumps":[false,false],"visionEnabled":true,"camera":{"connected":true}}
            ]}]}
        """.trimIndent()
        val viewModel = RobotViewModel(ApplicationProvider.getApplicationContext<Application>(), EmptyAuthStore)
        if (withClient) viewModel.apiClient = HydraApiClient("127.0.0.1", server.port)
        viewModel.state = HydraState(JSONObject(json))
        if (selected) viewModel.selectedRobotId.value = 1
        return viewModel
    }

    private fun takeBody(): JSONObject {
        val request = server.takeRequest(5, TimeUnit.SECONDS)
        assertNotNull("no request reached the server", request)
        assertEquals("/api/robot/1/command", request!!.path)
        return JSONObject(request.body.readUtf8())
    }

    @Test
    fun `a valve toggle sends its index and the new state and shows it at once`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        val viewModel = newViewModel()

        viewModel.toggleValve(1)

        assertTrue(viewModel.state.robotById(1)!!.valves.optBoolean(1))
        assertFalse(viewModel.state.robotById(1)!!.valves.optBoolean(0))
        val body = takeBody()
        assertEquals(RobotCommandContract.VALVE, body.getString("command"))
        assertEquals(1, body.getJSONObject("params").getInt("index"))
        assertTrue(body.getJSONObject("params").getBoolean("state"))
    }

    @Test
    fun `a pump toggled twice goes on and then off`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        val viewModel = newViewModel()

        viewModel.togglePump(0)
        assertTrue(takeBody().getJSONObject("params").getBoolean("state"))
        viewModel.togglePump(0)
        val second = takeBody()

        assertEquals(RobotCommandContract.PUMP, second.getString("command"))
        assertFalse(second.getJSONObject("params").getBoolean("state"))
        assertFalse(viewModel.state.robotById(1)!!.pumps.optBoolean(0))
    }

    @Test
    fun `the jog step goes out as the number it was given`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        val viewModel = newViewModel()

        viewModel.setJogStep(10.0)

        val body = takeBody()
        assertEquals(RobotCommandContract.JOG_STEP, body.getString("command"))
        assertEquals(10.0, body.getJSONObject("params").getDouble("value"), 0.0)
    }

    @Test
    fun `the vision switch of a robot goes out and the camera mirror follows it`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        val viewModel = newViewModel()

        viewModel.setVisionEnabled(1, false)

        val body = takeBody()
        assertEquals(RobotCommandContract.VISION, body.getString("command"))
        assertFalse(body.getJSONObject("params").getBoolean("enabled"))
        val raw = viewModel.state.robotById(1)!!.raw
        assertFalse(raw.getBoolean("visionEnabled"))
        assertFalse(raw.getJSONObject("camera").getBoolean("connected"))
    }

    @Test
    fun `nothing goes out and nothing changes while Listen only is on`() {
        val viewModel = newViewModel()

        // The state is set directly: setListenOnly() also saves the choice on another thread, and a saved "on" would leak into the tests that follow.
        viewModel.listenOnly.value = true

        viewModel.toggleValve(0)
        viewModel.togglePump(0)
        viewModel.setJogStep(5.0)

        assertEquals(0, server.requestCount)
        assertFalse(viewModel.state.robotById(1)!!.valves.optBoolean(0))
        assertNotNull(viewModel.lastError.value)
    }

    @Test
    fun `with no robot selected a valve or a pump toggle does nothing`() {
        val viewModel = newViewModel(selected = false)

        viewModel.toggleValve(0)
        viewModel.togglePump(0)

        assertEquals(0, server.requestCount)
        assertNull(viewModel.lastError.value)
    }

    @Test
    fun `with no client to send through the change is refused before it shows`() {
        val viewModel = newViewModel(withClient = false)

        viewModel.toggleValve(0)

        assertFalse(viewModel.state.robotById(1)!!.valves.optBoolean(0))
        assertNotNull(viewModel.lastError.value)
    }
}
