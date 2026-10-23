// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - app/src/test/java/com/hydraumc/control/network/HydraApiClientTlsModeTest.kt
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.network

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.TimeUnit

/**
 * How the three TLS modes steer the REST client. Robolectric only supplies org.json; the successful TLS
 * handshakes are covered in HydraHttpTlsTest, so only a plain local server is used here.
 */
@RunWith(RobolectricTestRunner::class)
class HydraApiClientTlsModeTest {
    private val info = """{"remoteApiVersion":2,"product":"HYDRA"}"""
    private val quick = HydraApiClient.sharedHttpClient.newBuilder()
        .connectTimeout(1, TimeUnit.SECONDS).readTimeout(1, TimeUnit.SECONDS).build()

    @After
    fun tearDown() {
        TlsSettingsStore.current = TlsSettings()
    }

    @Test fun `the mode decides the first scheme and whether https is tried later`() {
        assertTrue(HydraApiClient.startsWithTls(TlsMode.REQUIRED))
        assertFalse(HydraApiClient.startsWithTls(TlsMode.AUTO))
        assertFalse(HydraApiClient.startsWithTls(TlsMode.OFF))
        assertTrue(HydraApiClient.mayFallBackToHttps(TlsMode.AUTO))
        assertFalse(HydraApiClient.mayFallBackToHttps(TlsMode.REQUIRED))
        assertFalse(HydraApiClient.mayFallBackToHttps(TlsMode.OFF))
    }

    @Test fun `a new client follows the stored mode`() {
        assertTrue(HydraApiClient("192.168.0.180", 3000).baseUrl.startsWith("http://"))
        TlsSettingsStore.current = TlsSettings(TlsMode.REQUIRED)
        assertTrue(HydraApiClient("192.168.0.180", 3000).baseUrl.startsWith("https://"))
        TlsSettingsStore.current = TlsSettings(TlsMode.OFF)
        assertTrue(HydraApiClient("192.168.0.180", 3000).baseUrl.startsWith("http://"))
    }

    @Test fun `automatic and plain-only modes talk to a plain server and stay plain`() = runBlocking {
        for (mode in listOf(TlsMode.AUTO, TlsMode.OFF)) {
            val server = MockWebServer().also { it.enqueue(MockResponse().setBody(info)); it.start() }
            try {
                val api = HydraApiClient("127.0.0.1", server.port, quick, mode)
                assertNotNull(api.getHydraInfo())
                assertFalse(api.usesTls)
            } finally {
                server.shutdown()
            }
        }
    }

    @Test fun `required mode never falls back to plain`() = runBlocking {
        // The plain server would answer a downgraded request with a valid server description.
        val server = MockWebServer().also { it.enqueue(MockResponse().setBody(info)); it.start() }
        try {
            val api = HydraApiClient(server.hostName, server.port, quick, TlsMode.REQUIRED)
            assertTrue(api.baseUrl.startsWith("https://"))
            assertNull(api.getHydraInfo())
            assertTrue(api.baseUrl.startsWith("https://"))
        } finally {
            server.shutdown()
        }
    }
}
