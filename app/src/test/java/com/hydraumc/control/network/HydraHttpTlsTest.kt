// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - app/src/test/java/com/hydraumc/control/network/HydraHttpTlsTest.kt
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.network

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.fail
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import java.io.File
import java.io.IOException
import java.security.KeyStore
import java.security.cert.X509Certificate
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext

/**
 * Real TLS handshakes against a local server with a self-signed certificate made by the JDK's
 * keytool when the class starts: the pinned fingerprint, the custom CA, the default rejection and
 * and the fingerprint helpers. Pure JVM; nothing leaves the machine.
 */
class HydraHttpTlsTest {
    private lateinit var server: MockWebServer

    companion object {
        private lateinit var dir: File
        private lateinit var serverContext: SSLContext
        private lateinit var certificate: X509Certificate
        private lateinit var pem: String

        @BeforeClass
        @JvmStatic
        fun makeCertificate() {
            dir = File(System.getProperty("java.io.tmpdir"), "hydra-tls-test-${System.nanoTime()}").also { it.mkdirs() }
            val keytool = File(System.getProperty("java.home"), "bin/" + if (System.getProperty("os.name").lowercase().contains("win")) "keytool.exe" else "keytool")
            val store = File(dir, "server.p12")
            run(
                keytool.path, "-genkeypair", "-alias", "hydra", "-keyalg", "RSA", "-keysize", "2048", "-validity", "30",
                "-dname", "CN=hydra.invalid", "-ext", "san=dns:hydra.invalid", "-storetype", "PKCS12",
                "-keystore", store.path, "-storepass", "changeit", "-keypass", "changeit",
            )
            val keyStore = KeyStore.getInstance("PKCS12").apply { store.inputStream().use { load(it, "changeit".toCharArray()) } }
            certificate = keyStore.getCertificate("hydra") as X509Certificate
            val b64 = java.util.Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(certificate.encoded)
            pem = "-----BEGIN CERTIFICATE-----\n$b64\n-----END CERTIFICATE-----\n"
            val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply { init(keyStore, "changeit".toCharArray()) }
            serverContext = SSLContext.getInstance("TLS").apply { init(kmf.keyManagers, null, null) }
        }

        @AfterClass
        @JvmStatic
        fun cleanUp() {
            dir.deleteRecursively()
        }

        private fun run(vararg command: String) {
            val process = ProcessBuilder(*command).redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().readText()
            check(process.waitFor() == 0) { "keytool failed: $output" }
        }
    }

    private val info = """{"remoteApiVersion":2,"product":"HYDRA"}"""
    private val fingerprint get() = TlsFingerprint.of(certificate)

    @Before
    fun setUp() {
        server = MockWebServer()
        server.useHttps(serverContext.socketFactory, false)
        repeat(8) { server.enqueue(MockResponse().setBody(info)) }
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
        TlsSettingsStore.current = TlsSettings()
    }

    private fun get(client: OkHttpClient, host: String = server.hostName) =
        client.newCall(Request.Builder().url("https://$host:${server.port}/api/hydra-info").build()).execute().use { it.code }

    private fun clientFor(settings: TlsSettings) = HydraHttp.build(HydraApiClient.sharedHttpClient, settings)

    @Test fun `the system rules refuse a self-signed server`() {
        try {
            get(clientFor(TlsSettings()))
            fail("a self-signed certificate must not be trusted by default")
        } catch (_: IOException) {
        }
    }

    @Test fun `the pinned fingerprint accepts exactly that certificate`() {
        assertEquals(200, get(clientFor(TlsSettings(pinnedFingerprint = fingerprint))))
        assertEquals(200, get(clientFor(TlsSettings(pinnedFingerprint = TlsFingerprint.display(fingerprint)).normalized())))
    }

    @Test fun `the pin does not look at the host name`() {
        // The certificate names only hydra.invalid; the pin is the identity, so connecting by another name works.
        assertEquals(200, get(clientFor(TlsSettings(pinnedFingerprint = fingerprint)), server.hostName))
    }

    @Test fun `another fingerprint is refused`() {
        val other = fingerprint.reversed()
        try {
            get(clientFor(TlsSettings(pinnedFingerprint = other)))
            fail("a certificate with another fingerprint must be refused")
        } catch (_: IOException) {
        }
    }

    @Test fun `the pinned manager refuses client certificates and an empty chain`() {
        val manager = FingerprintTrustManager(fingerprint)
        manager.checkServerTrusted(arrayOf(certificate), "RSA")
        try { manager.checkServerTrusted(emptyArray<X509Certificate>(), "RSA"); fail() } catch (_: java.security.cert.CertificateException) {}
        try { manager.checkClientTrusted(arrayOf(certificate), "RSA"); fail() } catch (_: java.security.cert.CertificateException) {}
        assertEquals(0, manager.acceptedIssuers.size)
    }

    @Test fun `a custom ca is trusted when the host name matches the certificate`() {
        val client = clientFor(TlsSettings(customCaPem = pem)).newBuilder()
            .dns(object : okhttp3.Dns {
                override fun lookup(hostname: String) = listOf(java.net.InetAddress.getByName(server.hostName))
            })
            .build()
        assertEquals(200, get(client, "hydra.invalid"))
    }

    @Test fun `a custom ca still checks the host name`() {
        try {
            get(clientFor(TlsSettings(customCaPem = pem)))
            fail("the certificate names hydra.invalid, not ${server.hostName}")
        } catch (_: IOException) {
        }
    }

    @Test fun `settings that cannot be used leave the system rules in force`() {
        val base = HydraApiClient.sharedHttpClient
        assertSame(base, HydraHttp.build(base, TlsSettings(pinnedFingerprint = "nonsense")))
        assertSame(base, HydraHttp.build(base, TlsSettings(customCaPem = "nonsense")))
        assertSame(base, HydraHttp.build(base, TlsSettings()))
    }

    @Test fun `in plain-only mode neither the pin nor the ca is applied`() {
        val base = HydraApiClient.sharedHttpClient
        assertSame(base, HydraHttp.build(base, TlsSettings(TlsMode.OFF, fingerprint, pem)))
    }

    @Test fun `the shared client follows the stored settings and is reused while they stay`() {
        TlsSettingsStore.current = TlsSettings(pinnedFingerprint = fingerprint)
        val first = HydraHttp.client()
        assertSame(first, HydraHttp.client())
        assertEquals(200, get(first))
        TlsSettingsStore.current = TlsSettings()
        assertFalse(first === HydraHttp.client())
    }
}
