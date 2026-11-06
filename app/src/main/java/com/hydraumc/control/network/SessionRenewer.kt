// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - network/SessionRenewer.kt
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.network

import com.hydraumc.common.optStr
import org.json.JSONObject

/** What the renewal needs from the REST client (the client in the app, a fake in the tests). */
interface SessionEndpoint {
    var authToken: String?
    var refreshToken: String?
    suspend fun refresh(token: String): JSONObject
    suspend fun login(username: String, password: String): JSONObject
}

/** What the renewal needs from the stored login (AuthPrefs in the app, a fake in the tests). */
interface AuthStore {
    suspend fun loadAuth(): UserProfile
    suspend fun saveAuth(profile: UserProfile)

    /** Forgets the stored session; the password too when [wipePassword]. */
    suspend fun clearAuth(wipePassword: Boolean = true) {}
}

/**
 * Gets a new access token into a client: the refresh token first (no password needed), the stored "remember me"
 * credentials as a fallback. The password saved with "remember me" that the server refused once is not tried again
 * until [forgetRefusal] is called (the person signed in by hand). Nothing here touches Android, so it is tested on the JVM.
 */
class SessionRenewer(
    private val store: AuthStore,
    private val onToken: (String) -> Unit,
    private val log: (String) -> Unit,
) {
    private var savedCredentialsRefused = false

    fun forgetRefusal() {
        savedCredentialsRefused = false
    }

    /** True when a new token was stored in [client]; false is the caller's cue to send the person back to the sign-in. */
    suspend fun renew(client: SessionEndpoint): Boolean {
        return try {
            val profile = store.loadAuth()
            val refresh = client.refreshToken ?: profile.refreshToken.ifEmpty { null }
            var response: JSONObject? = null
            if (refresh != null) {
                response = try {
                    client.refresh(refresh).takeIf { it.optBoolean("success") }
                } catch (_: Exception) {
                    null
                }
            }
            if (response == null) {
                if (savedCredentialsRefused || !profile.rememberMe || profile.username.isBlank() || profile.password.isBlank()) return false
                val login = try {
                    client.login(profile.username, profile.password)
                } catch (e: Exception) {
                    // A 401/403/429 answer: do not insist with the same password.
                    if ((e as? HydraApiException)?.httpCode != null) savedCredentialsRefused = true
                    throw e
                }
                if (!login.optBoolean("success")) { savedCredentialsRefused = true; return false }
                response = login
            }
            val token = response.optStr("token")
            if (token.isEmpty()) return false
            client.authToken = token
            client.refreshToken = response.optStr("refreshToken").ifEmpty { null }
            onToken(token)
            store.saveAuth(profile.copy(token = token, refreshToken = client.refreshToken ?: "", isLoggedIn = true))
            true
        } catch (e: Exception) {
            log("Session renewal failed: ${e.message}")
            false
        }
    }
}
