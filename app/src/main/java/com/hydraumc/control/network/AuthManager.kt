// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - network/AuthManager.kt
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.network

import com.hydraumc.common.optStr
import org.json.JSONObject
import kotlin.coroutines.cancellation.CancellationException

/** What signing in and out need from the REST client (the client in the app, a fake in the tests). */
interface LoginEndpoint : SessionEndpoint {
    /** Revokes the refresh token on the server; best effort, never throws. */
    suspend fun logout(token: String)

    /**
     * Signs in with the code of the second factor of the account ([totp], an app code or a recovery code); null sends none. An endpoint that
     * knows no second factor ignores it.
     */
    suspend fun login(username: String, password: String, totp: String?): JSONObject = login(username, password)
}

/** Locks a password so that only a strong biometric opens it again (the Keystore in the app, a fake in the tests). */
interface PasswordSealer {
    /** The sealed password, or null when it cannot be sealed: the caller then keeps nothing. */
    fun seal(plain: String): String?

    /** Drops the key behind the sealed passwords. */
    fun forget()
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

    /** The account has a second factor and the sign-in carried no code: the form asks for one. */
    object NeedsCode : SignInResult()

    /** The code sent was refused (it is wrong, or already used). */
    object WrongCode : SignInResult()

    /** Too many wrong codes: the server asks to wait [seconds]. */
    data class Wait(val seconds: Int) : SignInResult()

    /** The call failed; [message] is what to show (the server's own text when it sent one) and [log] what to write in the log. */
    data class Failed(val message: String?, val log: String?) : SignInResult()
}

/**
 * The sign-in, the stored profile and the sign-out, away from the view model. The stored login is reached only through
 * [AuthStore], so all of it is tested on the JVM with a fake store (the encrypted store needs a real keystore).
 */
class AuthManager(private val store: AuthStore, private val sealer: PasswordSealer? = null) {

    /**
     * What is stored of the password: the plain text (only for "remember me" or the weak fingerprint sign-in) and the
     * sealed one. In the strong mode the plain text is never stored; a sealer that cannot seal stores nothing.
     */
    private fun passwordFields(password: String, remember: Boolean, biometric: Boolean, strong: Boolean, keepSealed: String): Pair<String, String> {
        if (biometric && strong) {
            if (password.isBlank()) return "" to keepSealed
            return "" to (sealer?.seal(password) ?: "")
        }
        sealer?.forget()
        return (if (remember || biometric) password else "") to ""
    }

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
        strong: Boolean = false,
        totp: String? = null,
    ): SignInResult {
        return try {
            val response = client.login(user, password, totp?.let(TotpRules::normalizeCode)?.takeIf { it.isNotEmpty() })
            if (!response.optBoolean("success")) return SignInResult.Rejected
            val token = response.optStr("token")
            val refresh = response.optStr("refreshToken").ifEmpty { null }
            client.authToken = token
            client.refreshToken = refresh
            val (plain, sealed) = passwordFields(password, remember, biometric, strong, "")
            store.saveAuth(
                UserProfile(
                    username = user,
                    password = plain,
                    email = email,
                    rememberMe = remember,
                    isLoggedIn = true,
                    isBiometricEnabled = biometric,
                    token = token,
                    refreshToken = refresh ?: "",
                    isStrongBiometric = biometric && strong,
                    sealedPassword = sealed,
                ),
            )
            SignInResult.Success(token, refresh)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: HydraApiException) {
            when {
                e.httpCode == 429 && e.totpRequired -> SignInResult.Wait(e.retryAfterSeconds?.takeIf { it > 0 } ?: TotpRules.DEFAULT_WAIT_SECONDS)
                e.httpCode == 401 && e.totpRequired -> if (totp.isNullOrBlank()) SignInResult.NeedsCode else SignInResult.WrongCode
                else -> SignInResult.Failed(e.serverMessage ?: e.message, e.message)
            }
        } catch (e: Exception) {
            SignInResult.Failed((e as? HydraApiException)?.serverMessage ?: e.message, e.message)
        }
    }

    /**
     * Saves the profile fields the person edited, starting from what is stored: the stored token is kept, because
     * writing an empty one would end the session at the next reconnect.
     */
    suspend fun saveProfile(
        user: String,
        password: String,
        email: String,
        biometric: Boolean,
        remember: Boolean,
        loggedIn: Boolean,
        strong: Boolean = false,
    ): UserProfile {
        val stored = store.loadAuth()
        val (plain, sealed) = passwordFields(password, remember, biometric, strong, stored.sealedPassword)
        val saved = stored.copy(
            username = user,
            password = plain,
            email = email,
            rememberMe = remember,
            isLoggedIn = loggedIn,
            isBiometricEnabled = biometric,
            isStrongBiometric = biometric && strong,
            sealedPassword = sealed,
        )
        store.saveAuth(saved)
        return saved
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
