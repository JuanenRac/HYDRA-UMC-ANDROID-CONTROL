// =============================================================================
// HYDRA-UMC CONTROL - Pairing by QR code or link
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.util

import com.hydraumc.control.network.RobotCommandContract
import com.hydraumc.control.network.TlsFingerprint
import java.net.URLDecoder

/**
 * What the admin page of the server puts in its QR code: where the server is, the fingerprint of its TLS
 * certificate (so the first connection is pinned instead of trusted blindly) and a name to show.
 *
 *     hydra-umc://pair?host=192.168.0.180&port=3000&fp=<64 hex digits>&name=Workshop
 *
 * This file only reads and checks the text; it connects to nothing and keeps nothing. The phone camera can open
 * such a link with this app, or the person pastes it; either way the app shows what it read and asks before it
 * keeps anything. The limits come from docs/robot_command_contract.json (generated RobotCommandContract).
 */
object PairingLink {
    /** What a link offers. [fingerprint] is the canonical (lowercase, no separators) SHA-256 of the certificate, or null for a server without TLS. */
    data class Offer(val host: String, val port: Int, val fingerprint: String?, val name: String) {
        val usesPinning: Boolean get() = fingerprint != null
    }

    enum class Problem { EMPTY, TOO_LONG, NOT_A_PAIRING_LINK, MISSING_HOST, BAD_HOST, BAD_PORT, BAD_FINGERPRINT, BAD_NAME, REPEATED_FIELD, BAD_ENCODING }

    sealed class Result {
        data class Ok(val offer: Offer) : Result()
        data class Refused(val problem: Problem) : Result()
    }

    private val PREFIX = "${RobotCommandContract.PAIRING_SCHEME}://${RobotCommandContract.PAIRING_HOST}"
    private val HOST_NAME = Regex("^[A-Za-z0-9]([A-Za-z0-9.-]{0,251}[A-Za-z0-9])?$")
    private val IPV4 = Regex("^(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})$")
    private val IPV6 = Regex("^[0-9A-Fa-f:]{2,45}$")

    /** True when [text] looks like a pairing link (it may still be refused by [parse]). */
    fun looksLikePairing(text: String?): Boolean = text != null && text.trim().startsWith("$PREFIX?", ignoreCase = true)

    fun parse(input: String?): Result {
        val text = input?.trim().orEmpty()
        if (text.isEmpty()) return Result.Refused(Problem.EMPTY)
        if (text.length > RobotCommandContract.PAIRING_MAX_PAYLOAD_CHARS) return Result.Refused(Problem.TOO_LONG)
        if (!looksLikePairing(text)) return Result.Refused(Problem.NOT_A_PAIRING_LINK)
        val query = text.substring(PREFIX.length + 1).substringBefore('#')
        val fields = HashMap<String, String>()
        for (pair in query.split('&')) {
            if (pair.isEmpty()) continue
            val key = pair.substringBefore('=').lowercase()
            val raw = pair.substringAfter('=', "")
            val value = try {
                URLDecoder.decode(raw, "UTF-8")
            } catch (_: IllegalArgumentException) {
                return Result.Refused(Problem.BAD_ENCODING)
            }
            // Only the four known fields count; a repeated one is refused so a link cannot say two things.
            if (key in KNOWN) {
                if (fields.containsKey(key)) return Result.Refused(Problem.REPEATED_FIELD)
                fields[key] = value
            }
        }
        val host = fields["host"]?.trim().orEmpty().removePrefix("[").removeSuffix("]")
        if (host.isEmpty()) return Result.Refused(Problem.MISSING_HOST)
        if (!validHost(host)) return Result.Refused(Problem.BAD_HOST)
        val port = fields["port"]?.trim()?.let { p -> if (p.isNotEmpty() && p.all { it.isDigit() } && p.length <= 5) p.toIntOrNull() else null }
        if (port == null || port !in RobotCommandContract.PAIRING_PORT_MIN..RobotCommandContract.PAIRING_PORT_MAX) return Result.Refused(Problem.BAD_PORT)
        val fpText = fields["fp"]?.trim().orEmpty()
        val fingerprint = if (fpText.isEmpty()) null else TlsFingerprint.normalize(fpText) ?: return Result.Refused(Problem.BAD_FINGERPRINT)
        val name = fields["name"]?.trim().orEmpty()
        if (name.length > RobotCommandContract.PAIRING_NAME_MAX || name.any { it.isISOControl() }) return Result.Refused(Problem.BAD_NAME)
        return Result.Ok(Offer(host, port, fingerprint, name.ifEmpty { host }))
    }

    private val KNOWN = setOf("host", "port", "fp", "name")

    private fun validHost(host: String): Boolean {
        IPV4.matchEntire(host)?.let { m -> return m.groupValues.drop(1).all { it.toInt() in 0..255 } }
        if (host.contains(':')) return IPV6.matches(host)
        return HOST_NAME.matches(host)
    }
}
