// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - app/src/test/java/com/hydraumc/control/MemoryAuthStore.kt
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control

import com.hydraumc.control.network.AuthStore
import com.hydraumc.control.network.SecretKv
import com.hydraumc.control.network.ServerSessions
import com.hydraumc.control.network.SessionFields
import com.hydraumc.control.network.UserProfile

/** A [SecretKv] kept in memory. */
class MemoryKv : SecretKv {
    val values = java.util.concurrent.ConcurrentHashMap<String, Any>()
    override fun getString(key: String): String? = values[key] as String?
    override fun getBoolean(key: String, default: Boolean): Boolean = values[key] as Boolean? ?: default

    @Synchronized
    override fun write(block: SecretKv.Writer.() -> Unit) {
        object : SecretKv.Writer {
            override fun putString(key: String, value: String) { values[key] = value }
            override fun putBoolean(key: String, value: Boolean) { values[key] = value }
            override fun remove(key: String) { values.remove(key) }
        }.block()
    }
}

/** The stored login of the app over [MemoryKv]: the same keys and the same [ServerSessions] rules, with no keystore. */
class MemoryAuthStore(val kv: MemoryKv = MemoryKv()) : AuthStore {
    override suspend fun loadAuth() = UserProfile(
        username = kv.getString("username").orEmpty(),
        password = kv.getString("password").orEmpty(),
        email = kv.getString("email").orEmpty(),
        rememberMe = kv.getBoolean("remember_me", false),
        isLoggedIn = kv.getBoolean("is_logged_in", false),
        token = kv.getString("token").orEmpty(),
        refreshToken = kv.getString("refresh_token").orEmpty(),
    )

    override suspend fun saveAuth(profile: UserProfile) {
        SessionFields(kv).saveSession(profile.token, profile.username, profile.refreshToken.ifEmpty { null }, replaceRefreshToken = true)
        kv.write {
            putString("password", profile.password)
            putString("email", profile.email)
            putBoolean("remember_me", profile.rememberMe)
            putBoolean("is_logged_in", profile.isLoggedIn)
        }
    }

    override suspend fun clearAuth(wipePassword: Boolean) {
        SessionFields(kv).clearSession()
        kv.write { putBoolean("is_logged_in", false) }
    }

    override suspend fun sessionServer(): String = kv.getString(ServerSessions.SERVER).orEmpty()
    override suspend fun bindSessionServer(id: String) = kv.write { putString(ServerSessions.SERVER, id) }
    override suspend fun switchServer(id: String) = ServerSessions.switchTo(kv, id)
    override suspend fun forgetServer(id: String) = ServerSessions.forget(kv, id)
    override suspend fun hasSealedPassword(ids: List<String>) = ServerSessions.anySealed(kv, ids)
}
