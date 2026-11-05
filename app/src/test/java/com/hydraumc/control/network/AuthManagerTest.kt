// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - app/src/test/java/com/hydraumc/control/network/AuthManagerTest.kt
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.network

import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The sign-in, the stored profile and the sign-out without Android: a fake store stands in for the encrypted one. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AuthManagerTest {

    private class Store(var profile: UserProfile = UserProfile("", "", "", rememberMe = false, isLoggedIn = false)) : AuthStore {
        var saved: UserProfile? = null
        var cleared: Boolean? = null
        var wipedPassword: Boolean? = null
        override suspend fun loadAuth(): UserProfile = profile
        override suspend fun saveAuth(profile: UserProfile) { saved = profile; this.profile = profile }
        override suspend fun clearAuth(wipePassword: Boolean) { cleared = true; wipedPassword = wipePassword }
    }

    private class Client(
        override var authToken: String? = null,
        override var refreshToken: String? = null,
        val onLogin: () -> JSONObject = { JSONObject().put("success", true).put("token", "tok").put("refreshToken", "ref") },
    ) : LoginEndpoint {
        var revoked: String? = null
        override suspend fun refresh(token: String): JSONObject = throw HydraApiException("no", httpCode = 401)
        override suspend fun login(username: String, password: String): JSONObject = onLogin()
        override suspend fun logout(token: String) { revoked = token }
    }

    @Test
    fun theFormNeedsEveryFieldAndANumericPort() {
        val auth = AuthManager(Store())
        assertEquals(LoginCheck.Missing, auth.check("", "3000", "ana", "pw"))
        assertEquals(LoginCheck.Missing, auth.check("10.0.0.2", "", "ana", "pw"))
        assertEquals(LoginCheck.Missing, auth.check("10.0.0.2", "3000", " ", "pw"))
        assertEquals(LoginCheck.Missing, auth.check("10.0.0.2", "3000", "ana", ""))
        assertEquals(LoginCheck.BadPort("abc"), auth.check("10.0.0.2", "abc", "ana", "pw"))
        assertEquals(LoginCheck.Valid("10.0.0.2", 3000, "ana", "pw"), auth.check(" 10.0.0.2 ", " 3000 ", "ana", "pw"))
    }

    @Test
    fun aSignInStoresTheTokensAndKeepsThePasswordOnlyWhenAsked() = runBlocking {
        val store = Store()
        val client = Client()
        val ok = AuthManager(store).signIn(client, "ana", "pw", remember = false, email = "a@b.c", biometric = false)
        assertEquals(SignInResult.Success("tok", "ref"), ok)
        assertEquals("tok", client.authToken)
        assertEquals("ref", client.refreshToken)
        assertEquals("", store.saved?.password)
        assertEquals("tok", store.saved?.token)
        assertEquals("ref", store.saved?.refreshToken)
        assertTrue(store.saved!!.isLoggedIn)

        AuthManager(store).signIn(Client(), "ana", "pw", remember = true, email = "", biometric = false)
        assertEquals("pw", store.saved?.password)
        AuthManager(store).signIn(Client(), "ana", "pw", remember = false, email = "", biometric = true)
        assertEquals("pw", store.saved?.password)
        assertTrue(store.saved!!.isBiometricEnabled)
    }

    @Test
    fun anAnswerWithoutSuccessIsARejectionAndStoresNothing() = runBlocking {
        val store = Store()
        val client = Client(onLogin = { JSONObject().put("success", false) })
        assertEquals(SignInResult.Rejected, AuthManager(store).signIn(client, "ana", "pw", true, "", false))
        assertNull(store.saved)
        assertNull(client.authToken)
    }

    @Test
    fun aFailureCarriesTheServerTextWhenThereIsOne() = runBlocking {
        val client = Client(onLogin = { throw HydraApiException("bad", httpCode = 401, serverMessage = "Wrong password") })
        val failed = AuthManager(Store()).signIn(client, "ana", "pw", true, "", false)
        assertTrue(failed is SignInResult.Failed)
        assertEquals("Wrong password", (failed as SignInResult.Failed).message)
    }

    @Test
    fun savingTheProfileStartsFromTheStoredSessionAndKeepsItsToken() = runBlocking {
        val store = Store(UserProfile("ana", "pw", "", rememberMe = true, isLoggedIn = true, token = "tok", refreshToken = "ref"))
        AuthManager(store).saveProfile("ana", "new", "a@b.c", biometric = false, remember = true, loggedIn = true)
        assertEquals("tok", store.saved?.token)
        assertEquals("ref", store.saved?.refreshToken)
        assertEquals("new", store.saved?.password)
        assertEquals("a@b.c", store.saved?.email)
        AuthManager(store).saveProfile("ana", "new", "", biometric = false, remember = false, loggedIn = true)
        assertEquals("", store.saved?.password)
    }

    @Test
    fun signingOutRevokesTheRefreshTokenAndWipesTheStore() = runBlocking {
        val store = Store(UserProfile("ana", "pw", "", true, true, refreshToken = "stored"))
        val client = Client(refreshToken = "live")
        AuthManager(store).signOut(client, keepPassword = false)
        assertEquals("live", client.revoked)
        assertTrue(store.cleared == true)
        assertTrue(store.wipedPassword == true)

        val fromStore = Client()
        AuthManager(store).signOut(fromStore, keepPassword = true)
        assertEquals("stored", fromStore.revoked)
        assertFalse(store.wipedPassword == true)
    }

    @Test
    fun signingOutWithNoClientStillWipesTheStore() = runBlocking {
        val store = Store()
        AuthManager(store).signOut(null, keepPassword = false)
        assertTrue(store.cleared == true)
    }
}
