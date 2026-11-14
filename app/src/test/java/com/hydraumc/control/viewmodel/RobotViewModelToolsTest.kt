// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - app/src/test/java/com/hydraumc/control/viewmodel/RobotViewModelToolsTest.kt
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
import com.hydraumc.control.network.RobotCommandContract
import com.hydraumc.control.util.WorkFiles
import kotlinx.coroutines.ExperimentalCoroutinesApi
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

/**
 * The tool changer and the speed slider through the view model, against a loopback [MockWebServer]: the slot is turned into the
 * name of the tool that sits in it (or "None"), the tool shows at once, and the speed goes out once, after the pause of the slider.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RobotViewModelToolsTest {
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

    private fun newViewModel(): RobotViewModel {
        val json = """
            {"activeControllerId":"c1","controllers":[{"id":"c1","robots":[
              {"id":1,"name":"R1","online":true,"enabled":true,"tool":"None",
               "atc":{"tools":[{"slot":1,"tool":"Gripper"},{"slot":2,"tool":"Laser"}]}}
            ]}]}
        """.trimIndent()
        val viewModel = RobotViewModel(ApplicationProvider.getApplicationContext<Application>(), EmptyAuthStore)
        viewModel.apiClient = HydraApiClient("127.0.0.1", server.port)
        viewModel.state = HydraState(JSONObject(json))
        viewModel.selectedRobotId.value = 1
        return viewModel
    }

    private fun takeBody(): JSONObject {
        val request = server.takeRequest(5, TimeUnit.SECONDS)
        assertNotNull("no request reached the server", request)
        assertEquals("/api/robot/1/command", request!!.path)
        return JSONObject(request.body.readUtf8())
    }

    @Test
    fun `changing to a slot sends the name of the tool in it and shows it at once`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        val viewModel = newViewModel()

        viewModel.changeTool(2)

        assertEquals("Laser", viewModel.state.robotById(1)!!.tool)
        val body = takeBody()
        assertEquals(RobotCommandContract.TOOL, body.getString("command"))
        assertEquals("Laser", body.getJSONObject("params").getString("tool"))
    }

    @Test
    fun `a slot with no tool in it sends None`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        val viewModel = newViewModel()

        viewModel.changeTool(7)

        assertEquals("None", takeBody().getJSONObject("params").getString("tool"))
    }

    @Test
    fun `a tool chosen by name goes out as it was given`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        val viewModel = newViewModel()

        viewModel.mutateSelectedTool("Gripper")

        assertEquals("Gripper", viewModel.state.robotById(1)!!.tool)
        assertEquals("Gripper", takeBody().getJSONObject("params").getString("tool"))
    }

    @Test
    fun `with no robot selected a slot change does nothing`() {
        val viewModel = newViewModel()
        viewModel.selectedRobotId.value = null

        viewModel.changeTool(1)

        assertEquals(0, server.requestCount)
    }

    @Test
    fun `a dragged slider shows its last value at once and waits for the pause before sending`() {
        val viewModel = newViewModel()

        viewModel.setSpeed(20.0, 100.0)
        viewModel.setSpeed(40.0, 150.0)
        viewModel.setSpeed(60.0, 200.0)

        assertEquals(60.0, viewModel.state.robotById(1)!!.speed, 0.0)
        // The command waits under the clock of the slider: nothing has reached the server yet.
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `a work file is refused with a reason while no client is connected`() {
        val viewModel = newViewModel()
        viewModel.apiClient = null

        assertTrue(viewModel.workBlock(1) != WorkFiles.Block.NONE)
        assertEquals(WorkFiles.Block.NOT_CONNECTED, viewModel.workBlock(1))
    }
}
