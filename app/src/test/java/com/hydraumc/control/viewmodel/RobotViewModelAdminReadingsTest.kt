// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - app/src/test/java/com/hydraumc/control/viewmodel/RobotViewModelAdminReadingsTest.kt
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.viewmodel

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.hydraumc.control.network.ConnectionsFailure
import com.hydraumc.control.network.HydraApiClient
import com.hydraumc.control.network.ServerOptionsFailure
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
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

/**
 * The three readings of the administration screens (the audit record, who connects, the access options of the server) against a real local HTTP
 * server: what each leaves in its state when the server answers, refuses, does not know the route, fails or does not answer, and that the busy flag
 * always goes down. They never raise an error on the screen: the reason is left in the failure of each reading.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RobotViewModelAdminReadingsTest {

    private lateinit var server: MockWebServer
    private val requests = mutableListOf<String>()

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

    private fun serverAnswering(answer: (String) -> MockResponse) {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                requests += "${request.method} ${request.path}"
                return answer(request.path ?: "")
            }
        }
    }

    private fun newViewModel(withClient: Boolean = true): RobotViewModel {
        val viewModel = RobotViewModel(ApplicationProvider.getApplicationContext<Application>())
        if (withClient) viewModel.apiClient = HydraApiClient("127.0.0.1", server.port).also { it.authToken = "tok" }
        return viewModel
    }

    private fun waitUntil(timeoutMs: Long = 8_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!condition() && System.currentTimeMillis() < deadline) Thread.sleep(20)
        assertTrue("condition not met within ${timeoutMs}ms", condition())
    }

    // ---- the audit record --------------------------------------------------------------------------------------------------------------

    @Test
    fun `the audit record without a connection is a network failure and is not busy`() {
        val viewModel = newViewModel(withClient = false)
        viewModel.refreshAudit(verify = false, type = null, robotId = null, limit = 50)
        assertEquals(AuditFailure.NETWORK, viewModel.auditFailure.value)
        assertFalse(viewModel.auditBusy.value)
    }

    @Test
    fun `the audit record maps the answers of the server to a reason`() {
        val cases = listOf(403 to AuditFailure.FORBIDDEN, 404 to AuditFailure.UNAVAILABLE, 500 to AuditFailure.OTHER, 502 to AuditFailure.OTHER)
        for ((code, expected) in cases) {
            serverAnswering { MockResponse().setResponseCode(code).setBody("{}") }
            val viewModel = newViewModel()
            viewModel.refreshAudit(verify = false, type = null, robotId = null, limit = 50)
            waitUntil { !viewModel.auditBusy.value && viewModel.auditFailure.value != null }
            assertEquals("status $code", expected, viewModel.auditFailure.value)
            assertNull(viewModel.auditRead.value)
            assertNull(viewModel.auditChain.value)
        }
    }

    @Test
    fun `the audit record asks for the filter the person chose`() {
        serverAnswering { MockResponse().setResponseCode(404).setBody("{}") }
        val viewModel = newViewModel()
        viewModel.refreshAudit(verify = true, type = "claim", robotId = 3, limit = 100)
        waitUntil { !viewModel.auditBusy.value && requests.isNotEmpty() }
        val path = requests.first()
        assertTrue(path, path.startsWith("GET /api/audit?"))
        for (part in listOf("limit=100", "type=claim", "robotId=3", "verify=1")) assertTrue("$part in $path", path.contains(part))
    }

    @Test
    fun `the audit record on a server that does not answer is a network failure`() {
        val viewModel = newViewModel()
        server.shutdown()
        viewModel.refreshAudit(verify = false, type = null, robotId = null, limit = 50)
        waitUntil { !viewModel.auditBusy.value && viewModel.auditFailure.value != null }
        assertEquals(AuditFailure.NETWORK, viewModel.auditFailure.value)
    }

    @Test
    fun `the export of the audit record gives the text, or null when it cannot be read`() {
        serverAnswering { MockResponse().setResponseCode(200).setBody("{\"a\":1}\n{\"a\":2}\n") }
        val viewModel = newViewModel()
        var text: String? = "unset"
        viewModel.exportAudit(type = null, robotId = null, limit = 50) { text = it }
        waitUntil { text != "unset" }
        assertEquals("{\"a\":1}\n{\"a\":2}\n", text)
        assertTrue(requests.last().contains("format=jsonl"))

        serverAnswering { MockResponse().setResponseCode(500).setBody("error") }
        var failed: String? = "unset"
        viewModel.exportAudit(type = null, robotId = null, limit = 50) { failed = it }
        waitUntil { failed != "unset" }
        assertNull(failed)

        var noClient: String? = "unset"
        newViewModel(withClient = false).exportAudit(type = null, robotId = null, limit = 50) { noClient = it }
        assertNull(noClient)
    }

    // ---- who connects ------------------------------------------------------------------------------------------------------------------

    @Test
    fun `the connections without a connection are a network failure and not busy`() {
        val viewModel = newViewModel(withClient = false)
        viewModel.refreshConnections()
        assertEquals(ConnectionsFailure.NETWORK, viewModel.connectionsFailure.value)
        assertFalse(viewModel.connectionsBusy.value)
    }

    @Test
    fun `the connections read the clients and the events and clear an old failure`() {
        serverAnswering { path ->
            when {
                path.startsWith("/api/connections/clients") -> MockResponse().setResponseCode(200).setBody("{\"clients\":[]}")
                path.startsWith("/api/connections/events") -> MockResponse().setResponseCode(200).setBody("{\"events\":[]}")
                else -> MockResponse().setResponseCode(404)
            }
        }
        val viewModel = newViewModel()
        viewModel.connectionsFailure.value = ConnectionsFailure.OTHER
        viewModel.refreshConnections()
        waitUntil { !viewModel.connectionsBusy.value && viewModel.connectionClients.value != null }
        assertEquals(emptyList<Any>(), viewModel.connectionClients.value)
        assertEquals(emptyList<Any>(), viewModel.connectionEvents.value)
        assertNull(viewModel.connectionsFailure.value)
        assertTrue(requests.any { it.startsWith("GET /api/connections/events?limit=30") || it.contains("/api/connections/events") })
    }

    @Test
    fun `the connections map the answers of the server to a reason and keep no stale list`() {
        val cases = listOf(404 to ConnectionsFailure.UNAVAILABLE, 500 to ConnectionsFailure.OTHER, 503 to ConnectionsFailure.OTHER)
        for ((code, expected) in cases) {
            serverAnswering { MockResponse().setResponseCode(code).setBody("{}") }
            val viewModel = newViewModel()
            viewModel.refreshConnections()
            waitUntil { !viewModel.connectionsBusy.value && viewModel.connectionsFailure.value != null }
            assertEquals("status $code", expected, viewModel.connectionsFailure.value)
            assertNull(viewModel.connectionClients.value)
            assertNull(viewModel.connectionEvents.value)
        }
    }

    // ---- the access options of the server ----------------------------------------------------------------------------------------------

    @Test
    fun `the access options without a connection are a network failure`() {
        val viewModel = newViewModel(withClient = false)
        viewModel.refreshServerOptions()
        assertEquals(ServerOptionsFailure.NETWORK, viewModel.serverOptionsFailure.value)
        assertFalse(viewModel.serverOptionsBusy.value)
    }

    @Test
    fun `the access options map the answers of the server to a reason`() {
        val cases = listOf(403 to ServerOptionsFailure.FORBIDDEN, 404 to ServerOptionsFailure.UNAVAILABLE, 500 to ServerOptionsFailure.OTHER)
        for ((code, expected) in cases) {
            serverAnswering { MockResponse().setResponseCode(code).setBody("{}") }
            val viewModel = newViewModel()
            viewModel.refreshServerOptions()
            waitUntil { !viewModel.serverOptionsBusy.value && viewModel.serverOptionsFailure.value != null }
            assertEquals("status $code", expected, viewModel.serverOptionsFailure.value)
            assertNull(viewModel.serverOptions.value)
        }
    }

    @Test
    fun `the access options are read and an answer that is not an object is a failure`() {
        serverAnswering { MockResponse().setResponseCode(200).setBody("{\"health\":{\"open\":{\"value\":true,\"source\":\"default\"}}}") }
        val viewModel = newViewModel()
        viewModel.refreshServerOptions()
        waitUntil { !viewModel.serverOptionsBusy.value && viewModel.serverOptions.value != null }
        assertNotNull(viewModel.serverOptions.value)
        assertNull(viewModel.serverOptionsFailure.value)

        serverAnswering { MockResponse().setResponseCode(200).setBody("not json") }
        val broken = newViewModel()
        broken.refreshServerOptions()
        waitUntil { !broken.serverOptionsBusy.value && broken.serverOptionsFailure.value != null }
        assertEquals(ServerOptionsFailure.OTHER, broken.serverOptionsFailure.value)
        assertNull(broken.serverOptions.value)
    }

    private val values = com.hydraumc.control.network.ServerSecurityValues(
        healthOpen = false, publishGlobalStop = false, recordAddress = false, cameraOpen = false, wsQueryToken = false, logRedaction = true, voiceSignature = true,
    )

    @Test
    fun `saving the access options needs a connection and options that were read first`() {
        val viewModel = newViewModel(withClient = false)
        viewModel.saveServerOptions(values)
        assertFalse(viewModel.serverOptionsBusy.value)
        assertFalse(viewModel.serverOptionsSaved.value)
        val withClient = newViewModel()
        withClient.saveServerOptions(values)
        assertFalse("nothing was read yet, so nothing is sent", withClient.serverOptionsBusy.value)
    }
}
