// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - app/src/test/java/com/hydraumc/control/network/TlsSettingsTest.kt
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The TLS choices of the user: defaults, validation and when the app warns about a plain connection. */
class TlsSettingsTest {
    private val hex = "ab".repeat(32)

    @Test fun `the defaults are the behaviour the app always had`() {
        val d = TlsSettings()
        assertEquals(TlsMode.AUTO, d.mode)
        assertEquals("", d.pinnedFingerprint)
        assertEquals("", d.customCaPem)
        assertNull(d.validate())
    }

    @Test fun `a fingerprint is accepted in any usual spelling`() {
        val colons = hex.uppercase().chunked(2).joinToString(":")
        assertEquals(hex, TlsFingerprint.normalize(colons))
        assertEquals(hex, TlsFingerprint.normalize(" $hex\n"))
        assertEquals(hex, TlsFingerprint.normalize(hex.chunked(2).joinToString("-")))
        assertEquals(colons, TlsFingerprint.display(hex))
    }

    @Test fun `a fingerprint of the wrong length or alphabet is refused`() {
        assertNull(TlsFingerprint.normalize("abcd"))
        assertNull(TlsFingerprint.normalize("zz".repeat(32)))
        assertNull(TlsFingerprint.normalize(hex + "ab"))
        assertEquals(TlsProblem.FINGERPRINT_INVALID, TlsSettings(pinnedFingerprint = "abcd").validate())
    }

    @Test fun `a ca that is not a certificate is refused`() {
        assertEquals(TlsProblem.CA_INVALID, TlsSettings(customCaPem = "not a certificate").validate())
        assertEquals(TlsProblem.CA_INVALID, TlsSettings(customCaPem = "-----BEGIN CERTIFICATE-----\nAAAA\n-----END CERTIFICATE-----").validate())
    }

    @Test fun `normalizing keeps the canonical fingerprint and trims the pem`() {
        val n = TlsSettings(TlsMode.REQUIRED, hex.uppercase(), "  ").normalized()
        assertEquals(hex, n.pinnedFingerprint)
        assertEquals("", n.customCaPem)
        assertEquals(TlsMode.REQUIRED, n.mode)
    }

    @Test fun `hosts on the local network are local`() {
        for (host in listOf(
            "192.168.0.180", "10.0.0.5", "172.16.0.1", "172.31.255.1", "127.0.0.1", "169.254.1.1",
            "localhost", "hydra-umc", "hydra.local", "cm5.lan", "printer.home.arpa", "::1", "[::1]", "fe80::1", "fd12:3456::1",
        )) {
            assertTrue("$host should be local", TlsPolicy.isLocalHost(host))
        }
    }

    @Test fun `public and unusual hosts are not local`() {
        for (host in listOf(
            "8.8.8.8", "172.32.0.1", "172.15.0.1", "192.169.0.1", "100.64.1.1", "example.com", "robot.example.org",
            "2001:db8::1", "999.1.1.1", "", "1.2.3",
        )) {
            assertFalse("$host should not be local", TlsPolicy.isLocalHost(host))
        }
    }

    @Test fun `only a plain session outside the local network warns`() {
        assertTrue(TlsPolicy.isPlainRemote("203.0.113.9", usesTls = false))
        assertTrue(TlsPolicy.isPlainRemote("robot.example.org", usesTls = false))
        assertFalse(TlsPolicy.isPlainRemote("203.0.113.9", usesTls = true))
        assertFalse(TlsPolicy.isPlainRemote("192.168.1.20", usesTls = false))
        assertFalse(TlsPolicy.isPlainRemote("hydra.local", usesTls = false))
    }
}
