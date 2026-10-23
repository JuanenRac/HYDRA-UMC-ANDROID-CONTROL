// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - HTTP client that follows the TLS settings of the user
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.network

import okhttp3.OkHttpClient
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/** Accepts one server certificate only: the one whose SHA-256 fingerprint is [expectedHex]. */
internal class FingerprintTrustManager(private val expectedHex: String) : X509TrustManager {
    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        throw CertificateException("client certificates are not accepted")
    }

    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        val leaf = chain?.firstOrNull() ?: throw CertificateException("the server sent no certificate")
        val actual = TlsFingerprint.of(leaf)
        if (!MessageDigest.isEqual(actual.toByteArray(), expectedHex.toByteArray())) {
            throw CertificateException("the server certificate does not match the pinned fingerprint")
        }
        leaf.checkValidity()
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
}

/** Builds and keeps the OkHttp client for the current [TlsSettings]. */
object HydraHttp {
    private var cachedFor: TlsSettings? = null
    private var cachedClient: OkHttpClient? = null

    /** The client for the settings in [TlsSettingsStore]; rebuilt only when they change. */
    @Synchronized
    fun client(): OkHttpClient {
        val settings = TlsSettingsStore.current
        val cached = cachedClient
        if (cached != null && cachedFor == settings) return cached
        return build(HydraApiClient.sharedHttpClient, settings).also {
            cachedFor = settings
            cachedClient = it
        }
    }

    /**
     * [base] with the trust rules of [settings]: the pinned fingerprint if there is one, else the
     * custom CA if there is one, else [base] untouched (system certificates). Unusable settings
     * (see [TlsSettings.validate]) are never applied: the system rules stay in force.
     */
    fun build(base: OkHttpClient, settings: TlsSettings): OkHttpClient {
        if (settings.mode == TlsMode.OFF) return base
        val pin = TlsFingerprint.normalize(settings.pinnedFingerprint)
        if (pin != null) {
            val manager = FingerprintTrustManager(pin)
            // The fingerprint names this exact certificate, so the host name check adds nothing.
            return withTrust(base, manager, HostnameVerifier { _, _ -> true })
        }
        val authorities = TlsPem.parse(settings.customCaPem)
        if (authorities.isNotEmpty()) {
            val store = KeyStore.getInstance(KeyStore.getDefaultType()).apply { load(null, null) }
            authorities.forEachIndexed { i, cert -> store.setCertificateEntry("hydra-ca-$i", cert) }
            val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply { init(store) }
            val manager = factory.trustManagers.filterIsInstance<X509TrustManager>().firstOrNull() ?: return base
            return withTrust(base, manager, null)
        }
        return base
    }

    private fun withTrust(base: OkHttpClient, manager: X509TrustManager, verifier: HostnameVerifier?): OkHttpClient {
        val context = SSLContext.getInstance("TLS").apply { init(null, arrayOf<TrustManager>(manager), SecureRandom()) }
        return base.newBuilder().sslSocketFactory(context.socketFactory, manager).apply {
            if (verifier != null) hostnameVerifier(verifier)
        }.build()
    }
}
