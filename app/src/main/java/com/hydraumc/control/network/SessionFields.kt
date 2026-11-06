// =============================================================================
// HYDRA-UMC CONTROL - Rules of the stored session (token, user name, refresh token)
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
// The Android side of the stored-session schema the mobile apps share (the
// SDK's docs/SESSION_STORE.md): the same rules as the Dart apps, over the
// encrypted store of this app, whose physical keys stay as they were so that
// nobody is signed out by an update. Nothing here touches Android, so the
// shared vectors (conformance/session-store/vectors.json) replay it on the JVM.
// =============================================================================
package com.hydraumc.control.network

import android.content.SharedPreferences

/** The few operations the session rules need from a key/value store. */
interface SecretKv {
    fun getString(key: String): String?
    fun getBoolean(key: String, default: Boolean): Boolean

    /** Applies every change of [block] together. */
    fun write(block: Writer.() -> Unit)

    interface Writer {
        fun putString(key: String, value: String)
        fun putBoolean(key: String, value: Boolean)
        fun remove(key: String)
    }
}

/** A [SecretKv] over (encrypted) shared preferences. */
class SharedPreferencesKv(private val prefs: SharedPreferences) : SecretKv {
    override fun getString(key: String): String? = prefs.getString(key, null)
    override fun getBoolean(key: String, default: Boolean): Boolean = prefs.getBoolean(key, default)
    override fun write(block: SecretKv.Writer.() -> Unit) {
        val edit = prefs.edit()
        object : SecretKv.Writer {
            override fun putString(key: String, value: String) { edit.putString(key, value) }
            override fun putBoolean(key: String, value: Boolean) { edit.putBoolean(key, value) }
            override fun remove(key: String) { edit.remove(key) }
        }.block()
        edit.apply()
    }
}

/** The physical keys of the session in this app's encrypted store (see the table in the SDK's SESSION_STORE.md). */
object SessionKeys {
    const val USERNAME = "username"
    const val TOKEN = "token"
    const val REFRESH_TOKEN = "refresh_token"
}

class SessionFields(private val kv: SecretKv) {

    /**
     * Stores the session. A null [refreshToken] keeps the stored one; with [replaceRefreshToken] it erases it (a
     * real sign-in), so a refresh token left by a previous account never becomes valid for the next one.
     */
    fun saveSession(token: String, username: String, refreshToken: String?, replaceRefreshToken: Boolean) {
        kv.write {
            putString(SessionKeys.TOKEN, token)
            putString(SessionKeys.USERNAME, username)
            if (refreshToken != null) putString(SessionKeys.REFRESH_TOKEN, refreshToken)
            else if (replaceRefreshToken) remove(SessionKeys.REFRESH_TOKEN)
        }
    }

    /** The access token, or null when there is none (an empty one counts as none). */
    fun token(): String? = kv.getString(SessionKeys.TOKEN)?.ifEmpty { null }

    fun username(): String? = kv.getString(SessionKeys.USERNAME)?.ifEmpty { null }

    fun refreshToken(): String? = kv.getString(SessionKeys.REFRESH_TOKEN)?.ifEmpty { null }

    /**
     * Ends the session: the token and the refresh token go. The user name stays (a platform difference the
     * schema notes) so the form can be filled again.
     */
    fun clearSession() {
        kv.write {
            remove(SessionKeys.TOKEN)
            remove(SessionKeys.REFRESH_TOKEN)
        }
    }
}
