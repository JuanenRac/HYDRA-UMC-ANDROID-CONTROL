// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - network/AuthManager.kt
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.network

import com.hydraumc.common.optStr
import kotlin.coroutines.cancellation.CancellationException

/** What signing in and out need from the REST client (the client in the app, a fake in the tests). */
interface LoginEndpoint : SessionEndpoint {
    /** Revokes the refresh token on the server; best effort, never throws. */
    suspend fun logout(token: String)
}

/** What the sign-in form holds, checked before anything goes to the server. */
sealed class LoginCheck {
    data class Valid(val host: String, val port: Int, val user: String, val password: String) : LoginCheck()

    /** The server address, the port, the user name or the password is empty. */
    object Missing : LoginCheck()

    /** The port is not a number. */
    data class BadPort(val text: String) : LoginCheck()
}

/** How a sign-in ended. */
sealed class SignInResult {
    data class Success(val token: String, val refreshToken: String?) : SignInResult()

    /** The server answered, but not with a session. */
    object Rejected : SignInResult()

    /** The call failed; [message] is what to show (the server's own text when it sent one) and [log] what to write in the log. */
    data class Failed(val message: String?, val log: String?) : SignInResult()
}

/**
 * The sign-in, the stored profile and the sign-out, away from the view model. The stored login is reached only through
 * [AuthStore], so all of it is tested on the JVM with a fake store (the encrypted store needs a real keystore).
 */
class AuthManager(private val store: AuthStore) {

    /** The same validation for the form and for a reconnect: nothing empty, and a port that is a number. */
    fun check(host: String, port: String, user: String, password: String): LoginCheck {
        val h = host.trim()
        val p = port.trim()
        if (h.isEmpty() || p.isEmpty() || user.isBlank() || password.isBlank()) return LoginCheck.Missing
        val portInt = p.toIntOrNull() ?: return LoginCheck.BadPort(p)
        return LoginCheck.Valid(h, portInt, user, password)
    }

    /**
     * Signs in with [client]. On success the tokens are in the client and the profile is stored; the password is kept
     * only when the person asked for it ("remember me", or the fingerprint sign-in, which needs it): the refresh token
     * renews the session without it.
     */
    suspend fun signIn(
        client: LoginEndpoint,
        user: String,
        password: String,
        remember: Boolean,
        email: String,
        biometric: Boolean,
    ): SignInResult {
        return try {
            val response = client.login(user, password)
            if (!response.optBoolean("success")) return SignInResult.Rejected
            val token = response.optStr("token")
            val refresh = response.optStr("refreshToken").ifEmpty { null }
            client.authToken = token
            client.refreshToken = refresh
            store.saveAuth(
                UserProfile(
                    username = user,
                    password = if (remember || biometric) password else "",
                    email = email,
                    rememberMe = remember,
                    isLoggedIn = true,
                    isBiometricEnabled = biometric,
                    token = token,
                    refreshToken = refresh ?: "",
                ),
            )
            SignInResult.Success(token, refresh)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            SignInResult.Failed((e as? HydraApiException)?.serverMessage ?: e.message, e.message)
        }
    }

    /**
     * Saves the profile fields the person edited, starting from what is stored: saving the profile used to write an
     * empty token, which ended the session at the next reconnect.
     */
    suspend fun saveProfile(
        user: String,
        password: String,
        email: String,
        biometric: Boolean,
        remember: Boolean,
        loggedIn: Boolean,
    ) {
        store.saveAuth(
            store.loadAuth().copy(
                username = user,
                password = if (remember || biometric) password else "",
                email = email,
                rememberMe = remember,
                isLoggedIn = loggedIn,
                isBiometricEnabled = biometric,
            ),
        )
    }

    /**
     * Ends the session for real: revokes the refresh token on the server (best effort) and wipes the stored login. The
     * saved password stays only when [keepPassword] (the fingerprint sign-in needs it).
     */
    suspend fun signOut(client: LoginEndpoint?, keepPassword: Boolean) {
        val refresh = client?.refreshToken ?: runCatching { store.loadAuth().refreshToken }.getOrDefault("").ifEmpty { null }
        if (client != null && refresh != null) client.logout(refresh)
        store.clearAuth(wipePassword = !keepPassword)
    }
}
