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

/** The token last told to a server, so that it can be taken back. */
interface PushTokenStore {
    fun load(): String?
    fun save(token: String?)
}

enum class PushResult { REGISTERED, SERVER_OFF, LISTEN_ONLY, FAILED }

private val SERVER_LANGUAGES = setOf("en", "es", "de", "fr", "it", "ja", "zh", "pt")

fun pushLocale(language: String): String = language.lowercase().takeIf { it in SERVER_LANGUAGES } ?: "en"

class PushRegistration(private val platform: PushPlatform, private val store: PushTokenStore) {
    val available: Boolean get() = platform.available

    suspend fun register(server: PushEndpoint, language: String): PushResult {
        if (!platform.available) return PushResult.FAILED
        val token = platform.token() ?: return PushResult.FAILED
        return try {
            server.registerPush(token, pushLocale(language))
            store.save(token)
            PushResult.REGISTERED
        } catch (e: HydraApiException) {
            when (e.httpCode) {
                404 -> PushResult.SERVER_OFF
                423 -> PushResult.LISTEN_ONLY
                else -> PushResult.FAILED
            }
        }
    }

    /** Takes the token back from [server] (when there is one to ask) and drops it at the push service. Never throws. */
    suspend fun remove(server: PushEndpoint?) {
        val token = store.load() ?: return
        store.save(null)
        try {
            server?.removePush(token)
        } catch (_: HydraApiException) {
            // the server forgets a dropped token the next time it tries to use it
        } finally {
            platform.forget()
        }
    }
}
