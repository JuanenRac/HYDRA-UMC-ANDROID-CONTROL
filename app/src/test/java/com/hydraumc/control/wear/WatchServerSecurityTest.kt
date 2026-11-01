// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - wear/WatchServerSecurityTest.kt
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.wear

import com.hydraumc.control.network.ConnectionClient
import com.hydraumc.control.network.OptionSource
import com.hydraumc.control.network.ServerOption
import com.hydraumc.control.network.ServerSecurityOptions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The read-only security status that the phone builds for the watch from the two answers an administrator can read. */
@RunWith(RobolectricTestRunner::class)
class WatchServerSecurityTest {
    private fun option(value: Boolean, source: OptionSource = OptionSource.DEFAULT) = ServerOption(value, source)

    /** Every option on its protected value, as a server starts. */
    private fun protectedOptions() = ServerSecurityOptions(
        healthOpen = option(false), publishGlobalStop = option(false), recordAddress = option(false), cameraOpen = option(false),
        wsQueryToken = option(false), logRedaction = option(true), voiceSignature = option(false), cameraCredentialsViaEnv = option(true), newInstallTls = option(true),
    )

    private fun client(active: Int = 0, failed: Int = 0) = ConnectionClient(
        id = "0123456789abcdef", kind = "watch", user = "ana", appVersion = "0.2.7", origin = "local", twoFactor = null,
        firstSeenMs = 1L, lastSeenMs = 2L, activeSessions = active, loginsOk = 1, loginsFailed = failed, ip = null,
    )

    @Test
    fun `a server with every option protected lists nothing`() {
        val status = WatchServerSecurity.build(protectedOptions(), emptyList())
        assertEquals(emptyList<String>(), status.loweredSwitches)
        assertEquals(0, status.lockedSwitches)
    }

    @Test
    fun `an option on the value that lowers the protection is listed with the id the server uses`() {
        val options = protectedOptions().copy(
            healthOpen = option(true), recordAddress = option(true), cameraOpen = option(true), wsQueryToken = option(true),
            logRedaction = option(false), cameraCredentialsViaEnv = option(false), newInstallTls = option(false),
        )
        assertEquals(
            listOf("health.open", "connections.recordAddress", "camera.open", "websocket.allowQueryToken", "logging.redactSecrets", "camera.credentialsViaEnv", "install.newInstallTls"),
            WatchServerSecurity.build(options, emptyList()).loweredSwitches,
        )
    }

    @Test
    fun `the announcement of the stop and the signature of the voice are not a lowering in either direction`() {
        val options = protectedOptions().copy(publishGlobalStop = option(true), voiceSignature = option(true))
        assertEquals(emptyList<String>(), WatchServerSecurity.build(options, emptyList()).loweredSwitches)
        assertEquals(emptyList<String>(), WatchServerSecurity.build(options.copy(voiceSignature = option(false)), emptyList()).loweredSwitches)
    }

    @Test
    fun `an option the server does not report is not counted as lowered`() {
        val options = ServerSecurityOptions(
            healthOpen = option(true), publishGlobalStop = null, recordAddress = null, cameraOpen = null, wsQueryToken = null,
            logRedaction = null, voiceSignature = null,
        )
        assertEquals(listOf("health.open"), WatchServerSecurity.build(options, emptyList()).loweredSwitches)
    }

    @Test
    fun `the options the environment fixes are counted`() {
        val options = protectedOptions().copy(healthOpen = option(false, OptionSource.ENV), logRedaction = option(true, OptionSource.ENV), voiceSignature = option(false, OptionSource.CONFIG))
        assertEquals(2, WatchServerSecurity.build(options, emptyList()).lockedSwitches)
    }

    @Test
    fun `the clients with an active session and the failed sign-ins come from the history`() {
        val status = WatchServerSecurity.build(protectedOptions(), listOf(client(active = 2, failed = 1), client(active = 0, failed = 4), client(active = 1)))
        assertEquals(2, status.activeClients)
        assertEquals(5, status.failedLogins)
    }

    @Test
    fun `the counts never go over what the watch accepts`() {
        val many = listOf(client(failed = Int.MAX_VALUE), client(failed = Int.MAX_VALUE))
        assertEquals(WatchServerSecurityStatus.MAX_COUNT, WatchServerSecurity.build(protectedOptions(), many).failedLogins)
    }

    @Test
    fun `two answers become a status and an answer without options becomes nothing`() {
        val config = """{"health":{"open":{"value":true,"source":"default"}},"logging":{"redactSecrets":{"value":true,"source":"env"}}}"""
        val clients = """{"clients":[{"id":"0123456789abcdef","kind":"watch","first_seen_utc":"2026-01-01T10:00:00Z","last_seen_utc":"2026-01-01T11:00:00Z","active_sessions":1,"logins_failed":2}]}"""
        val status = WatchServerSecurity.fromAnswers(config, clients)
        assertNotNull(status)
        assertEquals(listOf("health.open"), status!!.loweredSwitches)
        assertEquals(1, status.lockedSwitches)
        assertEquals(1, status.activeClients)
        assertEquals(2, status.failedLogins)
        assertNull(WatchServerSecurity.fromAnswers("{}", clients))
        assertNull(WatchServerSecurity.fromAnswers("not json", clients))
    }

    @Test
    fun `the message is written the way the watch reads it`() {
        val json = WatchServerSecurityStatus(listOf("health.open"), 1, 2, 3).toJson()
        assertEquals("server_security_status", json.getString("type"))
        assertEquals(1, json.getInt("protocolVersion"))
        assertEquals("""["health.open"]""", json.getJSONArray("loweredSwitches").toString())
        assertEquals(listOf(1, 2, 3), listOf(json.getInt("lockedSwitches"), json.getInt("activeClients"), json.getInt("failedLogins")))
        assertEquals(WatchServerSecurityStatus(listOf("health.open"), 1, 2, 3), WatchServerSecurityStatus.fromJson(json))
    }

    @Test
    fun `a message that breaks a rule is refused to be built`() {
        fun refused(block: () -> Unit) {
            try {
                block()
                fail("accepted")
            } catch (_: IllegalArgumentException) {
                // refused
            }
        }
        refused { WatchServerSecurityStatus(listOf("health"), 0, 0, 0) }
        refused { WatchServerSecurityStatus(listOf("a.b", "a.b"), 0, 0, 0) }
        refused { WatchServerSecurityStatus(('a'..'q').map { "s.$it" }, 0, 0, 0) }
        refused { WatchServerSecurityStatus(emptyList(), -1, 0, 0) }
        refused { WatchServerSecurityStatus(emptyList(), 0, 0, 100_001) }
        refused { WatchServerSecurityStatus(emptyList(), 0, 0, 0, protocolVersion = 2) }
        assertTrue(WatchServerSecurityStatus(('a'..'p').map { "s.$it" }, 0, 0, 0).loweredSwitches.size == 16)
    }
}
