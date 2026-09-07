// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - app/src/test/java/com/hydraumc/control/viewmodel/RobotViewModelSendVoiceTurnTest.kt
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0 - see LICENSE
// =============================================================================
package com.hydraumc.control.viewmodel

import android.app.Application
import androidx.test.core.app.ApplicationProvider
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
 * RobotViewModel.sendVoiceTurn() is this app's own in-app voice-assistant
 * button (VoiceAssistantDialog) - real coverage against a real (if local)
 * HTTP server ([MockWebServer]), same convention as
 * RobotViewModelSendAtomicCommandTest. The one behavior this specifically
 * guards against regressing: this call must NOT carry the
 * "X-Hydra-Client: watch" header postWatchVoiceTurn() sets for the separate
 * paired-Watch relay path (WatchVoiceRelayService) - the two are gated by
 * independent Config > Remote Access toggles on Server, and mixing them up
 * would let disabling one silently disable the other too.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RobotViewModelSendVoiceTurnTest {

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
        viewModel.apiClient = HydraApiClient("127.0.0.1", server.port)
        return viewModel
    }

    private fun waitUntil(timeoutMs: Long = 5_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!condition() && System.currentTimeMillis() < deadline) {
            Thread.sleep(20)
        }
        assertTrue("condition not met within ${timeoutMs}ms", condition())
    }

    private fun replyBody(text: String = "Robot 1 is idle.", requiresConfirmation: Boolean = false) = """
        {"type":"assistant_reply","requestId":"ignored","text":"$text","level":"INFO","speak":true,"requiresConfirmation":$requiresConfirmation}
    """.trimIndent()

    @Test
    fun `sends the real POST to the real voice turn endpoint without a client is not connected error`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody(replyBody()))
        val viewModel = newViewModel()

        viewModel.sendVoiceTurn("status for robot 1")

        val request = server.takeRequest(5, TimeUnit.SECONDS)
        assertEquals("POST", request?.method)
        assertEquals("/api/voice/turn", request?.path)
        val body = JSONObject(request!!.body.readUtf8())
        assertEquals("voice_turn", body.getString("type"))
        assertEquals("status for robot 1", body.getString("transcript"))
        // The shared interceptor (HydraApiClient.sharedHttpClient) always fills
        // this header, defaulting to "android" for any call that doesn't set
        // its own value - postVoiceTurn() deliberately doesn't, unlike
        // postWatchVoiceTurn()'s explicit "watch", so this call is gated by
        // Config > Remote Access > Android, never the separate Watch toggle.
        assertEquals("android", request.getHeader("X-Hydra-Client"))
    }

    @Test
    fun `a successful reply is exposed and busy resets to false`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody(replyBody("Robot 1 is idle.")))
        val viewModel = newViewModel()

        viewModel.sendVoiceTurn("status for robot 1")

        waitUntil { viewModel.latestVoiceAssistantReply.value != null }
        assertEquals("Robot 1 is idle.", viewModel.latestVoiceAssistantReply.value?.text)
        assertFalse(viewModel.voiceAssistantBusy.value)
        assertEquals(null, viewModel.lastError.value)
    }

    @Test
    fun `a failed request surfaces lastError and resets busy without a reply`() {
        server.enqueue(MockResponse().setResponseCode(500).setBody("server error"))
        val viewModel = newViewModel()

        viewModel.sendVoiceTurn("status for robot 1")

        waitUntil { viewModel.lastError.value != null }
        assertFalse(viewModel.voiceAssistantBusy.value)
        assertEquals(null, viewModel.latestVoiceAssistantReply.value)
    }

    @Test
    fun `refuses to send without a connected client instead of crashing`() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = RobotViewModel(application) // apiClient left null - never connected

        viewModel.sendVoiceTurn("status for robot 1")

        assertEquals(0, server.requestCount)
        assertTrue(viewModel.lastError.value?.contains("Connect to HYDRA-UMC-SERVER") == true)
    }

    @Test
    fun `clearVoiceAssistantReply resets the exposed reply`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody(replyBody()))
        val viewModel = newViewModel()
        viewModel.sendVoiceTurn("status for robot 1")
        waitUntil { viewModel.latestVoiceAssistantReply.value != null }

        viewModel.clearVoiceAssistantReply()

        assertEquals(null, viewModel.latestVoiceAssistantReply.value)
    }
}
