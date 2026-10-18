// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - app/src/test/java/com/hydraumc/control/viewmodel/RobotViewModelGlobalStopTest.kt
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0 - see LICENSE
// =============================================================================
package com.hydraumc.control.viewmodel

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.hydraumc.control.model.HydraState
import com.hydraumc.control.network.HydraApiClient
import com.hydraumc.control.network.StopAllStatus
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
 * The global STOP against a real local HTTP server: the call itself (path,
 * method, bearer header), the per-robot result, the retry after a network
 * error, the persistent "not confirmed" state and its clearing by a later
 * success, and the robot-by-robot fallback when the server rate limits.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RobotViewModelGlobalStopTest {

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

    private fun newViewModel(): RobotViewModel {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = RobotViewModel(application)
        viewModel.apiClient = HydraApiClient("127.0.0.1", server.port).also { it.authToken = "tok" }
        val state = HydraState(
            JSONObject(
                """{"activeControllerId":"c1","controllers":[{"id":"c1","robots":[
                    {"id":1,"name":"A","playbackState":{"isPlaying":true}},
                    {"id":2,"name":"B","playbackState":{"isPlaying":true}}]}]}""",
            ),
        )
        viewModel.state = state
        viewModel.selectedRobotId.value = 1
        return viewModel
    }

    private fun waitUntil(timeoutMs: Long = 8_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!condition() && System.currentTimeMillis() < deadline) Thread.sleep(20)
        assertTrue("condition not met within ${timeoutMs}ms", condition())
    }

    private fun finished(viewModel: RobotViewModel) =
        viewModel.stopAllStatus.value.let { it != null && it !is StopAllStatus.Sending }

    private val cleanAnswer = """{"success":true,"stoppedCount":2,"failedCount":0,"persisted":true,"robots":[
        {"robotId":1,"controllerId":"c1","name":"A","stopped":true,"valvesOff":true,"pumpsOff":true},
        {"robotId":2,"controllerId":"c1","name":"B","stopped":true,"valvesOff":true,"pumpsOff":true,"reservedBy":"alice"}]}"""

    @Test
    fun `one call stops every robot with the bearer token and no body`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody(cleanAnswer))
        val viewModel = newViewModel()

        viewModel.globalStop()
        waitUntil { finished(viewModel) }

        val request = server.takeRequest(1, TimeUnit.SECONDS)!!
        assertEquals("POST", request.method)
        assertEquals("/api/stop-all", request.path)
        assertEquals("Bearer tok", request.getHeader("Authorization"))
        assertEquals(0L, request.bodySize)
        assertEquals(1, server.requestCount)

        val done = viewModel.stopAllStatus.value as StopAllStatus.Done
        assertTrue(done.result.isClean)
        assertEquals(2, done.result.stoppedCount)
        assertEquals("alice", done.result.robots[1].reservedBy)
        assertFalse(viewModel.state.robotById(1)!!.isPlaying)
        assertFalse(viewModel.state.robotById(2)!!.isPlaying)
        assertNull(viewModel.lastError.value)
    }

    @Test
    fun `a failed robot or an unsaved stop is not a clean result`() {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"success":false,"stoppedCount":1,"failedCount":1,"persisted":false,"robots":[
                {"robotId":1,"controllerId":"c1","name":"A","stopped":true,"valvesOff":true,"pumpsOff":true},
                {"robotId":2,"controllerId":"c1","name":"B","stopped":false,"valvesOff":false,"pumpsOff":false,"error":"no link"}]}""",
            ),
        )
        val viewModel = newViewModel()

        viewModel.globalStop()
        waitUntil { finished(viewModel) }

        val result = (viewModel.stopAllStatus.value as StopAllStatus.Done).result
        assertFalse(result.isClean)
        assertFalse(result.persisted)
        assertEquals(listOf(2), result.failedRobots.map { it.robotId })
        assertEquals("no link", result.failedRobots[0].error)
        // Only the robot that stopped is shown as stopped.
        assertFalse(viewModel.state.robotById(1)!!.isPlaying)
        assertTrue(viewModel.state.robotById(2)!!.isPlaying)
    }

    @Test
    fun `a dropped connection is retried once and then succeeds`() {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
        server.enqueue(MockResponse().setResponseCode(200).setBody(cleanAnswer))
        val viewModel = newViewModel()

        viewModel.globalStop()
        waitUntil { finished(viewModel) }

        assertEquals(2, server.requestCount)
        assertTrue(viewModel.stopAllStatus.value is StopAllStatus.Done)
    }

    @Test
    fun `no answer at all leaves STOP not confirmed until a later stop works`() {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
        val viewModel = newViewModel()

        viewModel.globalStop()
        waitUntil { finished(viewModel) }

        assertEquals(2, server.requestCount)
        assertTrue(viewModel.stopAllStatus.value is StopAllStatus.NotConfirmed)
        assertNotNull(viewModel.lastError.value)
        // The banner cannot be dismissed away while the stop is unconfirmed.
        viewModel.dismissStopAllStatus()
        assertTrue(viewModel.stopAllStatus.value is StopAllStatus.NotConfirmed)

        server.enqueue(MockResponse().setResponseCode(200).setBody(cleanAnswer))
        viewModel.globalStop()
        waitUntil { viewModel.stopAllStatus.value is StopAllStatus.Done }
        assertNull(viewModel.lastError.value)
    }

    @Test
    fun `a rate limited call falls back to the stop of every known robot`() {
        server.enqueue(MockResponse().setResponseCode(429).setBody("""{"error":"too many requests"}"""))
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        val viewModel = newViewModel()

        viewModel.globalStop()
        waitUntil { finished(viewModel) }

        assertEquals("/api/stop-all", server.takeRequest().path)
        val paths = listOf(server.takeRequest(), server.takeRequest())
        assertEquals(setOf("/api/robot/1/command", "/api/robot/2/command"), paths.map { it.path }.toSet())
        assertTrue(paths.all { JSONObject(it.body.readUtf8()).getString("command") == "stop" })
        val result = (viewModel.stopAllStatus.value as StopAllStatus.Done).result
        assertTrue(result.viaFallback)
        assertEquals(2, result.stoppedCount)
    }

    @Test
    fun `the fallback reports the robot that would not stop`() {
        server.enqueue(MockResponse().setResponseCode(429).setBody("{}"))
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        server.enqueue(MockResponse().setResponseCode(409).setBody("""{"error":"reserved"}"""))
        val viewModel = newViewModel()

        viewModel.globalStop()
        waitUntil { finished(viewModel) }

        val result = (viewModel.stopAllStatus.value as StopAllStatus.Done).result
        assertEquals(1, result.failedCount)
        assertFalse(result.isClean)
    }

    @Test
    fun `with no connection STOP is shown as not confirmed`() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = RobotViewModel(application)

        viewModel.globalStop()

        assertTrue(viewModel.stopAllStatus.value is StopAllStatus.NotConfirmed)
        assertNotNull(viewModel.lastError.value)
    }

    @Test
    fun `a server refusal other than rate limit is not confirmed and not retried`() {
        server.enqueue(MockResponse().setResponseCode(500).setBody("""{"error":"boom"}"""))
        val viewModel = newViewModel()

        viewModel.globalStop()
        waitUntil { finished(viewModel) }

        assertEquals(1, server.requestCount)
        assertTrue(viewModel.stopAllStatus.value is StopAllStatus.NotConfirmed)
    }
}
