// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - app/src/test/java/com/hydraumc/control/viewmodel/RobotViewModelHomeResetTest.kt
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.viewmodel

import android.app.Application
import android.util.Base64
import androidx.test.core.app.ApplicationProvider
import com.hydraumc.control.MainDispatcherSwap
import com.hydraumc.control.model.HydraState
import com.hydraumc.control.network.HydraApiClient
import com.hydraumc.control.util.HomeReset
import kotlinx.coroutines.ExperimentalCoroutinesApi
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

/**
 * HOME/RESET through the view model, against a loopback [MockWebServer]: no real robot is ever involved. It checks
 * that nothing goes out while the switch is off or a rule blocks it, that the command carries its number, and that
 * the refusals of the server turn into their own phrases.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RobotViewModelHomeResetTest {
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

    private fun newViewModel(playing: Boolean = false, enabled: Boolean = true, online: Boolean = true): RobotViewModel {
        val json = """
            {"activeControllerId":"c1","controllers":[{"id":"c1","robots":[
              {"id":1,"name":"R1","online":$online,"enabled":$enabled,"pos":{"x":10,"y":20,"z":30,"a":1,"b":2,"c":3},"playbackState":{"isPlaying":$playing}}
            ]}]}
        """.trimIndent()
        val viewModel = RobotViewModel(ApplicationProvider.getApplicationContext<Application>())
        viewModel.apiClient = HydraApiClient("127.0.0.1", server.port)
        viewModel.state = HydraState(JSONObject(json))
        viewModel.selectedRobotId.value = 1
        return viewModel
    }

    private fun waitUntil(timeoutMs: Long = 5_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!condition() && System.currentTimeMillis() < deadline) Thread.sleep(20)
        assertTrue("condition not met within ${timeoutMs}ms", condition())
    }

    private fun jwt(role: String): String {
        fun part(s: String) = Base64.encodeToString(s.toByteArray(), Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
        return part("""{"alg":"none"}""") + "." + part("""{"role":"$role"}""") + ".sig"
    }

    @Test
    fun `nothing goes out while the switch is off`() {
        // The default (off) is checked against the contract in HomeResetTest; here the switch is set off explicitly so the
        // saved choice of an earlier test cannot leak in.
        val viewModel = newViewModel()
        viewModel.setHomeResetEnabled(false)
        assertFalse(viewModel.homeResetEnabled.value)
        assertEquals(HomeReset.Block.OFF, viewModel.homeResetBlock(1))

        viewModel.homeReset()

        assertEquals(0, server.requestCount)
        assertNotNull(viewModel.lastError.value)
    }

    @Test
    fun `with the switch on the reset goes out numbered and shows zero at once`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        val viewModel = newViewModel()
        viewModel.setHomeResetEnabled(true)

        viewModel.homeReset()

        assertEquals(0.0, viewModel.state.robotById(1)!!.posAxis("x"), 0.0)
        val request = server.takeRequest(5, TimeUnit.SECONDS)
        assertEquals("/api/robot/1/command", request?.path)
        val body = JSONObject(request!!.body.readUtf8())
        assertEquals("reset", body.getString("command"))
        assertEquals("robot", body.getJSONObject("params").getString("target"))
        assertTrue(body.getLong("seq") > 0)
    }

    @Test
    fun `a robot that is playing or in safe mode or offline is not reset`() {
        for (viewModel in listOf(newViewModel(playing = true), newViewModel(enabled = false), newViewModel(online = false))) {
            viewModel.setHomeResetEnabled(true)
            assertTrue(viewModel.homeResetBlock(1) != HomeReset.Block.NONE)
            viewModel.homeReset()
        }
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `a session that is neither operator nor administrator is not offered it`() {
        val viewModel = newViewModel()
        viewModel.setHomeResetEnabled(true)
        viewModel.authTokenState.value = jwt("viewer")
        assertEquals(HomeReset.Block.ROLE, viewModel.homeResetBlock(1))
        viewModel.authTokenState.value = jwt("operator")
        assertEquals(HomeReset.Block.NONE, viewModel.homeResetBlock(1))
        viewModel.authTokenState.value = jwt("admin")
        assertEquals(HomeReset.Block.NONE, viewModel.homeResetBlock(1))
    }

    @Test
    fun `an older server that does not know reset is remembered and the pose is restored`() {
        server.enqueue(MockResponse().setResponseCode(400).setBody("""{"error":"unknown command"}"""))
        val viewModel = newViewModel()
        viewModel.setHomeResetEnabled(true)

        viewModel.homeReset()
        waitUntil { viewModel.lastError.value != null }

        assertFalse(viewModel.homeResetServerSupports.value)
        assertEquals(HomeReset.Block.NOT_SUPPORTED, viewModel.homeResetBlock(1))
        assertEquals(10.0, viewModel.state.robotById(1)!!.posAxis("x"), 0.0)
        // A second press sends nothing more.
        viewModel.homeReset()
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `a 403 from the server keeps the robot where it was and says who may do it`() {
        server.enqueue(MockResponse().setResponseCode(403).setBody("""{"error":"forbidden"}"""))
        val viewModel = newViewModel()
        viewModel.setHomeResetEnabled(true)

        viewModel.homeReset()
        waitUntil { viewModel.lastError.value != null }

        assertEquals(10.0, viewModel.state.robotById(1)!!.posAxis("x"), 0.0)
        assertTrue(viewModel.homeResetServerSupports.value)
    }
}
