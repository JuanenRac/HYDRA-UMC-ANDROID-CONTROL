// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - app/src/test/java/com/hydraumc/control/viewmodel/RobotViewModelWorkFilesTest.kt
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.viewmodel

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.hydraumc.control.MainDispatcherSwap
import com.hydraumc.control.model.HydraState
import com.hydraumc.control.network.HydraApiClient
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

/** The work-file list and the reservations through the view model, against a loopback [MockWebServer]: no real robot is involved. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RobotViewModelWorkFilesTest {
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

    private fun newViewModel(connected: Boolean = true): RobotViewModel {
        val json = """
            {"activeControllerId":"c1","controllers":[{"id":"c1","robots":[
              {"id":1,"name":"Arm One","online":true,"enabled":true,"pos":{"x":1,"y":2,"z":3,"a":0,"b":0,"c":0}}
            ]}]}
        """.trimIndent()
        val viewModel = RobotViewModel(ApplicationProvider.getApplicationContext<Application>())
        if (connected) viewModel.apiClient = HydraApiClient("127.0.0.1", server.port)
        viewModel.state = HydraState(JSONObject(json))
        viewModel.selectedRobotId.value = 1
        return viewModel
    }

    private fun waitUntil(timeoutMs: Long = 5_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!condition() && System.currentTimeMillis() < deadline) Thread.sleep(20)
        assertTrue("condition not met within ${timeoutMs}ms", condition())
    }

    @Test
    fun `the folder of a robot without a configured one is derived from its name`() {
        assertEquals("WORKS/ArmOne", newViewModel().workFolderOf(1))
        assertNull(newViewModel().workFolderOf(99))
    }

    @Test
    fun `the list of a folder is read from its index`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""["a.json","b.json"]"""))
        val viewModel = newViewModel()
        viewModel.loadWorkList(1)
        waitUntil { viewModel.workFiles.value != null && !viewModel.workBusy.value }
        assertEquals(listOf("a.json", "b.json"), viewModel.workFiles.value)
        assertEquals("/WORKS/ArmOne/index.json", server.takeRequest(5, TimeUnit.SECONDS)?.path)
    }

    @Test
    fun `a folder that does not exist is an empty list and not an error`() {
        server.enqueue(MockResponse().setResponseCode(404))
        val viewModel = newViewModel()
        viewModel.lastError.value = null
        viewModel.loadWorkList(1)
        waitUntil { viewModel.workFiles.value != null && !viewModel.workBusy.value }
        assertEquals(emptyList<String>(), viewModel.workFiles.value)
        assertNull(viewModel.lastError.value)
    }

    @Test
    fun `a server error while listing says so and leaves the list empty`() {
        server.enqueue(MockResponse().setResponseCode(500))
        val viewModel = newViewModel()
        viewModel.loadWorkList(1)
        waitUntil { viewModel.lastError.value != null && !viewModel.workBusy.value }
        assertNull(viewModel.workFiles.value)
    }

    @Test
    fun `without a connection nothing is requested`() {
        val viewModel = newViewModel(connected = false)
        viewModel.loadWorkList(1)
        assertNotNull(viewModel.lastError.value)
        assertEquals(0, server.requestCount)
        assertFalse(viewModel.workBusy.value)
    }

    @Test
    fun `a file name that is not safe is never requested`() {
        val viewModel = newViewModel()
        viewModel.selectWork(1, "../secret.json")
        assertNotNull(viewModel.lastError.value)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `a work file with a bad content is refused before anything goes to the robot`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("not a list of points"))
        val viewModel = newViewModel()
        viewModel.selectWork(1, "bad.json")
        waitUntil { viewModel.lastError.value != null && !viewModel.workBusy.value }
        assertEquals(1, server.requestCount)
        assertEquals("/WORKS/ArmOne/bad.json", server.takeRequest(5, TimeUnit.SECONDS)?.path)
    }

    @Test
    fun `reserving without a connection sends nothing and says so`() {
        val viewModel = newViewModel(connected = false)
        viewModel.claimSelectedRobot()
        assertNotNull(viewModel.lastError.value)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `a command on a robot that is not in the state says it was not found`() {
        val viewModel = newViewModel()
        viewModel.selectedRobotId.value = 42
        viewModel.sendCommand("stop")
        assertNotNull(viewModel.lastError.value)
        assertEquals(0, server.requestCount)
    }
}
