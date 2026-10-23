// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - app/src/test/java/com/hydraumc/control/network/PlainRemoteGuardTest.kt
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.network

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Plain traffic leaves the local network only when the user allowed it. */
class PlainRemoteGuardTest {
    private fun client(settings: TlsSettings) = OkHttpClient.Builder().addInterceptor(PlainRemoteGuard { settings }).build()

    @Test fun `the default refuses plain traffic to a public host without opening a connection`() {
        assertFalse(TlsSettings().allowPlainRemote)
        try {
            client(TlsSettings()).newCall(Request.Builder().url("http://203.0.113.9:3000/api/info").build()).execute()
            fail("a plain request to a public address must be refused")
        } catch (e: PlainRemoteBlockedException) {
            assertEquals("203.0.113.9", e.host)
        }
    }

    @Test fun `a public host name is refused too`() {
        try {
            client(TlsSettings()).newCall(Request.Builder().url("http://robots.example.com/").build()).execute()
            fail("a plain request to a public name must be refused")
        } catch (e: PlainRemoteBlockedException) {
            assertEquals("robots.example.com", e.host)
        }
    }

    @Test fun `a local server over plain HTTP still works`() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("ok"))
            server.start()
            val response = client(TlsSettings()).newCall(Request.Builder().url("http://127.0.0.1:${server.port}/").build()).execute()
            assertEquals("ok", response.body?.string())
        }
    }

    @Test fun `private addresses are local and the allow switch lifts the rule`() {
        assertTrue(TlsPolicy.isLocalHost("192.168.0.180"))
        assertTrue(TlsPolicy.isLocalHost("10.1.2.3"))
        assertTrue(TlsPolicy.isLocalHost("172.16.0.1"))
        assertFalse(TlsPolicy.isLocalHost("172.32.0.1"))
        val allowed = TlsSettings(allowPlainRemote = true)
        // The guard lets the request through; no server answers on this test address, so a network error follows.
        try {
            client(allowed).newCall(Request.Builder().url("http://127.0.0.1:1/").build()).execute()
            fail("nothing listens on port 1")
        } catch (e: PlainRemoteBlockedException) {
            fail("allowed settings must not block")
        } catch (_: java.io.IOException) {
            // expected: connection refused
        }
        // A public address with the switch on is not blocked by the guard either (the connect times out or fails).
        val quick = OkHttpClient.Builder().addInterceptor(PlainRemoteGuard { allowed })
            .connectTimeout(1, java.util.concurrent.TimeUnit.MILLISECONDS).build()
        try {
            quick.newCall(Request.Builder().url("http://203.0.113.9:3000/").build()).execute()
        } catch (e: PlainRemoteBlockedException) {
            fail("allowed settings must not block")
        } catch (_: java.io.IOException) {
            // any other network failure is fine
        }
    }
}
