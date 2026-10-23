// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - Optional, configurable TLS toward the server
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.network

import java.io.ByteArrayInputStream
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

/** How the app reaches the server. */
enum class TlsMode {
    /** Plain HTTP first, HTTPS when only HTTPS answers: what the app always did. The default. */
    AUTO,
    /** HTTPS and WSS only; a server without TLS is never used. */
    REQUIRED,
    /** Plain HTTP and WS only; HTTPS is never tried. */
    OFF,
}

/** What is wrong with a [TlsSettings] the user typed. */
enum class TlsProblem { FINGERPRINT_INVALID, CA_INVALID }

/**
 * The TLS choices of the user. With a [pinnedFingerprint] only a server whose certificate has exactly
 * that SHA-256 fingerprint is accepted (host name and issuer are not checked: the fingerprint is the
 * identity, which is what a self-signed server needs). Without it, a [customCaPem] replaces the system
 * certificates as the only trusted issuer. With neither, the system certificates decide.
 * Both are ignored in [TlsMode.OFF].
 */
data class TlsSettings(
    val mode: TlsMode = TlsMode.AUTO,
    val pinnedFingerprint: String = "",
    val customCaPem: String = "",
) {
    /** The first problem found, or null when the settings can be saved. Blank fields are fine. */
    fun validate(): TlsProblem? {
        if (pinnedFingerprint.isNotBlank() && TlsFingerprint.normalize(pinnedFingerprint) == null) return TlsProblem.FINGERPRINT_INVALID
        if (customCaPem.isNotBlank() && TlsPem.parse(customCaPem).isEmpty()) return TlsProblem.CA_INVALID
        return null
    }

    /** The settings with the fingerprint in its canonical form (lowercase hex, no separators) and the PEM trimmed. */
    fun normalized(): TlsSettings = copy(
        pinnedFingerprint = TlsFingerprint.normalize(pinnedFingerprint) ?: pinnedFingerprint.trim(),
        customCaPem = customCaPem.trim(),
    )
}

/** The settings the clients read when they are created; the ViewModel updates it after loading and saving. */
object TlsSettingsStore {
    @Volatile
    var current: TlsSettings = TlsSettings()
}

/** SHA-256 fingerprints of certificates. */
object TlsFingerprint {
    private val HEX64 = Regex("^[0-9a-f]{64}$")

    /** Lowercase hex without separators, or null when [input] is not 64 hexadecimal characters (colons, spaces and dashes are ignored). */
    fun normalize(input: String): String? {
        val cleaned = input.filterNot { it == ':' || it == '-' || it.isWhitespace() }.lowercase()
        return cleaned.takeIf { HEX64.matches(it) }
    }

    /** Fingerprint of [certificate] as lowercase hex. */
    fun of(certificate: X509Certificate): String =
        MessageDigest.getInstance("SHA-256").digest(certificate.encoded).joinToString("") { "%02x".format(it) }

    /** The usual display form: uppercase pairs separated by colons. */
    fun display(hex: String): String = hex.uppercase().chunked(2).joinToString(":")
}

/** PEM certificates. */
object TlsPem {
    /** Every certificate in [pem]; an empty list when the text holds none or is damaged. */
    fun parse(pem: String): List<X509Certificate> = try {
        CertificateFactory.getInstance("X.509")
            .generateCertificates(ByteArrayInputStream(pem.trim().toByteArray(Charsets.US_ASCII)))
            .filterIsInstance<X509Certificate>()
    } catch (_: Exception) {
        // CertificateException for damaged text; anything else is also "no usable certificate".
        emptyList()
    }
}

/** Where a server is: decides when to warn about an unencrypted connection. */
object TlsPolicy {
    /** True for loopback, private (RFC 1918), link-local and unique-local addresses, and for single-label or .local/.lan/.home.arpa names. */
    fun isLocalHost(host: String): Boolean {
        val h = host.trim().trim('[', ']').lowercase()
        if (h.isEmpty()) return false
        if (h == "localhost") return true
        if (h.contains(':')) return isLocalIpv6(h)
        val octets = h.split('.')
        if (octets.size == 4 && octets.all { it.toIntOrNull() in 0..255 }) {
            val a = octets[0].toInt()
            val b = octets[1].toInt()
            return a == 10 || a == 127 || (a == 172 && b in 16..31) || (a == 192 && b == 168) || (a == 169 && b == 254)
        }
        if (octets.all { it.all(Char::isDigit) && it.isNotEmpty() }) return false // an incomplete or odd number, not a name
        return octets.size == 1 || h.endsWith(".local") || h.endsWith(".lan") || h.endsWith(".home.arpa")
    }

    private fun isLocalIpv6(h: String): Boolean =
        h == "::1" || h.startsWith("fe80:") || h.startsWith("fc") || h.startsWith("fd")

    /** True when a session is plain (no TLS) and the server is outside the local network: the app warns. */
    fun isPlainRemote(host: String, usesTls: Boolean): Boolean = !usesTls && !isLocalHost(host)
}
