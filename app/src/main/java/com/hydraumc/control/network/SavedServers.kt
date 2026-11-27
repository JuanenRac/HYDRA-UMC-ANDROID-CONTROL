// =============================================================================
// HYDRA-UMC CONTROL - The servers this phone keeps
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
// The list holds nothing secret: where each server is, the name to show and how its certificate is checked. The session of
// each one is in the encrypted store (see ServerSessions). A server enters the list only after a sign-in to it worked.
package com.hydraumc.control.network

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** One kept server. [tls] travels with it, so changing server never checks a certificate with the rules of another one. */
data class SavedServer(val host: String, val port: Int, val name: String, val tls: TlsSettings = TlsSettings()) {
    val id: String get() = SessionBinding.id(host, port)

    /** What the screens and the STOP say: the name when it tells more than the address, and always the address. */
    val label: String get() = if (name.isBlank() || name.equals(host, ignoreCase = true)) "$host:$port" else "$name ($host:$port)"

    /** The short form for the small places (the STOP button). */
    val shortName: String get() = name.ifBlank { host }
}

object SavedServerList {
    const val MAX = 12
    private const val NAME_MAX = 64

    /** Adds [server], or replaces the entry of the same address where it is. Past [MAX] the oldest one falls off. */
    fun upsert(list: List<SavedServer>, server: SavedServer): List<SavedServer> {
        val clean = server.copy(host = server.host.trim(), name = server.name.trim().take(NAME_MAX))
        if (list.any { it.id == clean.id }) return list.map { if (it.id == clean.id) clean else it }
        return (list + clean).takeLast(MAX)
    }

    fun remove(list: List<SavedServer>, id: String): List<SavedServer> = list.filterNot { it.id == id }

    fun encode(list: List<SavedServer>): String = JSONArray().also { array ->
        list.forEach {
            array.put(
                JSONObject().put("host", it.host).put("port", it.port).put("name", it.name).put("tls", it.tls.mode.name)
                    .put("pin", it.tls.pinnedFingerprint).put("ca", it.tls.customCaPem).put("plainRemote", it.tls.allowPlainRemote),
            )
        }
    }.toString()

    /** A damaged entry is dropped, never an error. An unknown TLS mode is read as "required", never as a weaker one. */
    fun decode(text: String): List<SavedServer> = runCatching {
        val array = JSONArray(text)
        (0 until array.length()).mapNotNull { i ->
            val entry = array.optJSONObject(i) ?: return@mapNotNull null
            val host = entry.optString("host").trim()
            val port = entry.optInt("port", 0)
            if (host.isEmpty() || port !in 1..65535) return@mapNotNull null
            val tls = TlsSettings(
                mode = TlsMode.entries.firstOrNull { it.name == entry.optString("tls") } ?: TlsMode.REQUIRED,
                pinnedFingerprint = entry.optString("pin"),
                customCaPem = entry.optString("ca"),
                allowPlainRemote = entry.optBoolean("plainRemote", false),
            )
            SavedServer(host, port, entry.optString("name"), tls)
        }.distinctBy { it.id }.take(MAX)
    }.getOrDefault(emptyList())
}

class SavedServerStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("hydra_saved_servers", Context.MODE_PRIVATE)

    fun load(): List<SavedServer> = SavedServerList.decode(prefs.getString(KEY, "[]") ?: "[]")

    fun save(list: List<SavedServer>) {
        prefs.edit().putString(KEY, SavedServerList.encode(list)).apply()
    }

    private companion object {
        const val KEY = "list"
    }
}
