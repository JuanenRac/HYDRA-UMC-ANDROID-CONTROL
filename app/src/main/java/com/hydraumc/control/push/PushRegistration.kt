// =============================================================================
// HYDRA-UMC CONTROL - Registering this phone for the push messages of a server
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
// The decisions, apart from Android: the token of the phone is told to a server only with a signed-in session, and is
// taken back when the person switches the messages off or signs out.
package com.hydraumc.control.push

import com.hydraumc.control.network.HydraApiException

/** The two calls of the server this needs. */
interface PushEndpoint {
    suspend fun registerPush(token: String, locale: String)
    suspend fun removePush(token: String)
}

/** The token last told to each server, so that it can be taken back from that one. */
interface PushTokenStore {
    fun load(server: String): String?
    fun save(server: String, token: String?)

    /** The servers that still hold a token of this phone. */
    fun servers(): Set<String>
    fun clear()
}

enum class PushResult { REGISTERED, SERVER_OFF, LISTEN_ONLY, FAILED }

private val SERVER_LANGUAGES = setOf("en", "es", "de", "fr", "it", "ja", "zh", "pt")

fun pushLocale(language: String): String = language.lowercase().takeIf { it in SERVER_LANGUAGES } ?: "en"

class PushRegistration(private val platform: PushPlatform, private val store: PushTokenStore) {
    val available: Boolean get() = platform.available

    suspend fun register(server: PushEndpoint, serverId: String, language: String): PushResult {
        if (!platform.available) return PushResult.FAILED
        val token = platform.token() ?: return PushResult.FAILED
        return try {
            server.registerPush(token, pushLocale(language))
            store.save(serverId, token)
            PushResult.REGISTERED
        } catch (e: HydraApiException) {
            when (e.httpCode) {
                404 -> PushResult.SERVER_OFF
                423 -> PushResult.LISTEN_ONLY
                else -> PushResult.FAILED
            }
        }
    }

    /**
     * Takes the registration of [serverId] back, asking [server] when there is a session to ask with. The token of the
     * phone is one for every server: it is dropped at the push service when no other server keeps it, and also when
     * [serverId] could not be told, so that it cannot go on sending. The others register again when they are next used.
     * Never throws.
     */
    suspend fun remove(server: PushEndpoint?, serverId: String) {
        val token = store.load(serverId) ?: return
        store.save(serverId, null)
        var told = false
        try {
            if (server != null) {
                server.removePush(token)
                told = true
            }
        } catch (_: HydraApiException) {
            // not told: the token is dropped below
        } finally {
            if (!told || store.servers().isEmpty()) drop()
        }
    }

    /** The switch goes off: the server in use is told, and the token is dropped for all of them. Never throws. */
    suspend fun removeAll(server: PushEndpoint?, serverId: String?) {
        if (store.servers().isEmpty()) return
        val token = serverId?.let { store.load(it) }
        try {
            if (token != null) server?.removePush(token)
        } catch (_: HydraApiException) {
            // the server forgets a dropped token the next time it tries to use it
        } finally {
            drop()
        }
    }

    /** Drops the token at the push service: no server reaches this phone until it registers again. */
    fun drop() {
        store.clear()
        platform.forget()
    }
}
