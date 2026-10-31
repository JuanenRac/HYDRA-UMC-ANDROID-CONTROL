// =============================================================================
// HYDRA-UMC CONTROL - who connects to the server (list of clients and history of sign-ins)
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
// The two answers of the server (GET /api/connections/clients and /events) in the closed shape of the ConnectionClient and
// ConnectionEvent contracts of the SDK: no password, token or cookie is in either. The address and the user agent come only
// for an administrator and only when the server was told to keep them; this app shows them when they come and never asks.
package com.hydraumc.control.network

import java.util.Calendar
import java.util.TimeZone
import org.json.JSONObject

/** The kinds of client the server tells apart. */
val CONNECTION_KINDS = listOf("studio", "suite", "android", "ios", "dsi", "watch", "voice", "other")

/** The things the history writes down. */
val CONNECTION_ACTIONS = listOf("first_seen", "login", "login_failed", "second_factor_failed", "logout", "session_ended", "session_expired")

/** One application the server has seen. */
data class ConnectionClient(
    val id: String,
    val kind: String,
    val user: String?,
    val appVersion: String?,
    /** "local" or "remote", or null when the server did not say. */
    val origin: String?,
    val twoFactor: Boolean?,
    val firstSeenMs: Long,
    val lastSeenMs: Long,
    val activeSessions: Int,
    val loginsOk: Int,
    val loginsFailed: Int,
    val ip: String?,
)

/** One line of the history. */
data class ConnectionEvent(
    val id: Long,
    val atMs: Long,
    val action: String,
    val kind: String,
    val user: String?,
    val appVersion: String?,
) {
    /** A refused sign-in or a wrong code of the second step: painted as a warning. */
    val isFailure: Boolean get() = action == "login_failed" || action == "second_factor_failed"
}

/** Why the history could not be read, for the sentence the screen shows. */
enum class ConnectionsFailure { UNAVAILABLE, SESSION, NETWORK, OTHER }

object ConnectionHistory {
    private val ID = Regex("^[0-9a-f]{16}$")

    private val UTC_DATE = Regex("""^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2}):(\d{2})(?:\.(\d{1,9}))?Z$""")

    /** An ISO date in UTC as the server writes it (`2026-01-02T03:04:05.678Z`) in milliseconds; null for anything else. (java.time needs Android 8.) */
    internal fun millis(text: String?): Long? {
        val m = UTC_DATE.matchEntire(text ?: return null) ?: return null
        val parts = (1..6).map { m.groupValues[it].toInt() }
        val (y, mo, d) = parts
        val (h, mi, se) = parts.drop(3)
        if (mo !in 1..12 || d !in 1..31 || h > 23 || mi > 59 || se > 59) return null
        val ms = m.groupValues[7].padEnd(3, '0').take(3).toInt()
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        calendar.clear()
        calendar.set(y, mo - 1, d, h, mi, se)
        return calendar.timeInMillis + ms
    }

    /** A string field, or null when it is absent, null or empty (the plain optString answers an empty text for those). */
    private fun text(json: JSONObject, name: String): String? =
        if (!json.has(name) || json.isNull(name)) null else json.optString(name, "").takeIf { it.isNotEmpty() }

    private fun kindOf(text: String?): String = if (text != null && text in CONNECTION_KINDS) text else "other"

    /** Reads one client; null when it is not a well-formed entry (a bad identifier or date). */
    fun parseClient(json: JSONObject?): ConnectionClient? {
        json ?: return null
        val id = text(json, "id") ?: return null
        if (!ID.matches(id)) return null
        val first = millis(text(json, "first_seen_utc")) ?: return null
        val last = millis(text(json, "last_seen_utc")) ?: return null
        return ConnectionClient(
            id = id,
            kind = kindOf(text(json, "kind")),
            user = text(json, "user"),
            appVersion = text(json, "app_version"),
            origin = text(json, "origin")?.takeIf { it == "local" || it == "remote" },
            twoFactor = if (json.has("two_factor")) json.optBoolean("two_factor") else null,
            firstSeenMs = first,
            lastSeenMs = last,
            activeSessions = json.optInt("active_sessions", 0).coerceAtLeast(0),
            loginsOk = json.optInt("logins_ok", 0).coerceAtLeast(0),
            loginsFailed = json.optInt("logins_failed", 0).coerceAtLeast(0),
            ip = text(json, "ip"),
        )
    }

    /** Reads one event; null when it is not a well-formed entry or its action is not one the server writes. */
    fun parseEvent(json: JSONObject?): ConnectionEvent? {
        json ?: return null
        val action = text(json, "action") ?: return null
        if (action !in CONNECTION_ACTIONS) return null
        val at = millis(text(json, "at_utc")) ?: return null
        val id = json.optLong("id", 0L)
        if (id < 1) return null
        return ConnectionEvent(
            id = id,
            atMs = at,
            action = action,
            kind = kindOf(text(json, "client_kind")),
            user = text(json, "user"),
            appVersion = text(json, "app_version"),
        )
    }

    /** The clients of an answer `{ "clients": [...] }`, the most recently seen first; what cannot be read is left out. */
    fun parseClients(body: String): List<ConnectionClient> {
        val list = JSONObject(body).optJSONArray("clients") ?: return emptyList()
        return (0 until list.length()).mapNotNull { parseClient(list.optJSONObject(it)) }.sortedByDescending { it.lastSeenMs }
    }

    /** The events of an answer `{ "events": [...] }`, newest first. */
    fun parseEvents(body: String): List<ConnectionEvent> {
        val list = JSONObject(body).optJSONArray("events") ?: return emptyList()
        return (0 until list.length()).mapNotNull { parseEvent(list.optJSONObject(it)) }.sortedByDescending { it.id }
    }

    /** How long ago, short enough for a line: "30 s", "12 min", "3 h", "2 d". */
    fun ago(thenMs: Long, nowMs: Long): Pair<Long, String> {
        val seconds = ((nowMs - thenMs) / 1000).coerceAtLeast(0)
        return when {
            seconds < 60 -> seconds to "s"
            seconds < 3600 -> (seconds / 60) to "min"
            seconds < 48 * 3600 -> (seconds / 3600) to "h"
            else -> (seconds / 86_400) to "d"
        }
    }
}
