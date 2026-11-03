// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - app/src/test/java/com/hydraumc/control/network/SessionRenewerTest.kt
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

/** The renewal of the session without Android: refresh token first, saved login as the fallback, a refused password not repeated. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SessionRenewerTest {

    private class Store(var profile: UserProfile) : AuthStore {
        var saved: UserProfile? = null
        override suspend fun loadAuth(): UserProfile = profile
        override suspend fun saveAuth(profile: UserProfile) { saved = profile }
    }

    private class Client(
        override var authToken: String? = null,
        override var refreshToken: String? = null,
        val onRefresh: (String) -> JSONObject = { throw HydraApiException("no", httpCode = 401) },
        val onLogin: (String, String) -> JSONObject = { _, _ -> throw HydraApiException("no", httpCode = 401) },
    ) : SessionEndpoint {
        var refreshCalls = 0
        var loginCalls = 0
        override suspend fun refresh(token: String): JSONObject { refreshCalls++; return onRefresh(token) }
        override suspend fun login(username: String, password: String): JSONObject { loginCalls++; return onLogin(username, password) }
    }

    private fun profile(remember: Boolean = true, refresh: String = "", password: String = "pw") =
        UserProfile(username = "ana", password = password, email = "", rememberMe = remember, isLoggedIn = true, refreshToken = refresh)

    private fun ok(token: String, refresh: String = "") =
        JSONObject().put("success", true).put("token", token).put("refreshToken", refresh)

    private fun renewer(store: Store, tokens: MutableList<String> = mutableListOf(), logs: MutableList<String> = mutableListOf()) =
        SessionRenewer(store, { tokens += it }, { logs += it })

    @Test
    fun theRefreshTokenRenewsWithoutThePassword() = runBlocking {
        val store = Store(profile(refresh = "stored-refresh"))
        val tokens = mutableListOf<String>()
        val client = Client(onRefresh = { assertEquals("stored-refresh", it); ok("new-token", "rotated") })
        assertTrue(renewer(store, tokens).renew(client))
        assertEquals(0, client.loginCalls)
        assertEquals("new-token", client.authToken)
        assertEquals("rotated", client.refreshToken)
        assertEquals(listOf("new-token"), tokens)
        assertEquals("rotated", store.saved?.refreshToken)
        assertEquals("new-token", store.saved?.token)
    }

    @Test
    fun theSavedLoginIsTheFallbackWhenTheRefreshFails() = runBlocking {
        val store = Store(profile(refresh = "stale"))
        val client = Client(onLogin = { u, p -> assertEquals("ana", u); assertEquals("pw", p); ok("login-token") })
        assertTrue(renewer(store).renew(client))
        assertEquals(1, client.refreshCalls)
        assertEquals(1, client.loginCalls)
        assertEquals("login-token", client.authToken)
        assertNull(client.refreshToken)
    }

    @Test
    fun withoutRememberMeThereIsNoFallback() = runBlocking {
        val client = Client()
        assertFalse(renewer(Store(profile(remember = false))).renew(client))
        assertEquals(0, client.loginCalls)
    }

    @Test
    fun aRefusedPasswordIsNotTriedAgainUntilForgotten() = runBlocking {
        val client = Client()
        val renewer = renewer(Store(profile()))
        assertFalse(renewer.renew(client))
        assertEquals(1, client.loginCalls)
        assertFalse(renewer.renew(client))
        assertEquals(1, client.loginCalls)
        renewer.forgetRefusal()
        assertFalse(renewer.renew(client))
        assertEquals(2, client.loginCalls)
    }

    @Test
    fun aLoginThatSaysNoCountsAsRefused() = runBlocking {
        val client = Client(onLogin = { _, _ -> JSONObject().put("success", false) })
        val renewer = renewer(Store(profile()))
        assertFalse(renewer.renew(client))
        assertFalse(renewer.renew(client))
        assertEquals(1, client.loginCalls)
    }

    @Test
    fun aNetworkFailureIsNotARefusal() = runBlocking {
        var calls = 0
        val client = Client(onLogin = { _, _ -> calls++; throw HydraApiException("offline") })
        val renewer = renewer(Store(profile()))
        assertFalse(renewer.renew(client))
        assertFalse(renewer.renew(client))
        assertEquals(2, calls)
    }

    @Test
    fun anAnswerWithoutTokenFailsAndAStoreThatThrowsIsLogged() = runBlocking {
        val client = Client(onLogin = { _, _ -> JSONObject().put("success", true) })
        assertFalse(renewer(Store(profile())).renew(client))
        val logs = mutableListOf<String>()
        val broken = object : AuthStore {
            override suspend fun loadAuth(): UserProfile = throw IllegalStateException("keystore")
            override suspend fun saveAuth(profile: UserProfile) {}
        }
        assertFalse(SessionRenewer(broken, {}, { logs += it }).renew(Client()))
        assertTrue(logs.single().contains("keystore"))
    }
}
