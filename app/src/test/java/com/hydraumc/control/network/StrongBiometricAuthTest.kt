// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - app/src/test/java/com/hydraumc/control/network/StrongBiometricAuthTest.kt
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.network

import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The strong biometric mode of the stored login: the password is only ever stored sealed, and the mode is off by default. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StrongBiometricAuthTest {

    private class Store(var profile: UserProfile = UserProfile("", "", "", rememberMe = false, isLoggedIn = false)) : AuthStore {
        override suspend fun loadAuth(): UserProfile = profile
        override suspend fun saveAuth(profile: UserProfile) { this.profile = profile }
    }

    private class Sealer(private val works: Boolean = true) : PasswordSealer {
        var forgotten = 0
        override fun seal(plain: String): String? = if (works) "sealed:" + plain.reversed() else null
        override fun forget() { forgotten++ }
    }

    private class Client : LoginEndpoint {
        override var authToken: String? = null
        override var refreshToken: String? = null
        override suspend fun refresh(token: String): JSONObject = throw HydraApiException("no", httpCode = 401)
        override suspend fun login(username: String, password: String): JSONObject =
            JSONObject().put("success", true).put("token", "tok").put("refreshToken", "ref")
        override suspend fun logout(token: String) {}
    }

    @Test
    fun theStrongModeIsOffByDefault() {
        val profile = UserProfile("ana", "", "", rememberMe = false, isLoggedIn = false)
        assertFalse(profile.isStrongBiometric)
        assertEquals("", profile.sealedPassword)
    }

    @Test
    fun aStrongSignInStoresOnlyTheSealedPassword() = runBlocking {
        val store = Store()
        AuthManager(store, Sealer()).signIn(Client(), "ana", "pw", remember = true, email = "", biometric = true, strong = true)
        assertEquals("", store.profile.password)
        assertEquals("sealed:wp", store.profile.sealedPassword)
        assertTrue(store.profile.isStrongBiometric)
        assertTrue(store.profile.isBiometricEnabled)
    }

    @Test
    fun theWeakModeKeepsTheCurrentBehaviourAndDropsTheKey() = runBlocking {
        val store = Store()
        val sealer = Sealer()
        AuthManager(store, sealer).signIn(Client(), "ana", "pw", remember = false, email = "", biometric = true)
        assertEquals("pw", store.profile.password)
        assertEquals("", store.profile.sealedPassword)
        assertFalse(store.profile.isStrongBiometric)
        assertEquals(1, sealer.forgotten)
    }

    @Test
    fun aKeyThatCannotSealStoresNoPasswordAtAll() = runBlocking {
        val store = Store()
        AuthManager(store, Sealer(works = false)).signIn(Client(), "ana", "pw", remember = true, email = "", biometric = true, strong = true)
        assertEquals("", store.profile.password)
        assertEquals("", store.profile.sealedPassword)
    }

    @Test
    fun withoutASealerTheStrongModeStoresNoPassword() = runBlocking {
        val store = Store()
        AuthManager(store).signIn(Client(), "ana", "pw", remember = true, email = "", biometric = true, strong = true)
        assertEquals("", store.profile.password)
        assertEquals("", store.profile.sealedPassword)
    }

    @Test
    fun editingTheProfileWithAnEmptyPasswordKeepsTheSealedOne() = runBlocking {
        val store = Store(UserProfile("ana", "", "", true, true, isBiometricEnabled = true, isStrongBiometric = true, sealedPassword = "sealed:wp"))
        val saved = AuthManager(store, Sealer()).saveProfile("ana", "", "a@b.c", biometric = true, remember = true, loggedIn = true, strong = true)
        assertEquals("sealed:wp", saved.sealedPassword)
        assertEquals("", saved.password)
        assertEquals("a@b.c", saved.email)
        val renewed = AuthManager(store, Sealer()).saveProfile("ana", "new", "", biometric = true, remember = true, loggedIn = true, strong = true)
        assertEquals("sealed:wen", renewed.sealedPassword)
    }

    @Test
    fun turningTheModeOffDropsTheSealedPasswordAndTheKey() = runBlocking {
        val store = Store(UserProfile("ana", "", "", true, true, isBiometricEnabled = true, isStrongBiometric = true, sealedPassword = "sealed:wp"))
        val sealer = Sealer()
        val saved = AuthManager(store, sealer).saveProfile("ana", "pw", "", biometric = true, remember = true, loggedIn = true, strong = false)
        assertEquals("", saved.sealedPassword)
        assertFalse(saved.isStrongBiometric)
        assertEquals("pw", saved.password)
        assertEquals(1, sealer.forgotten)
    }

    @Test
    fun theStrongModeNeedsTheWeakSwitchOn() = runBlocking {
        val store = Store()
        val saved = AuthManager(store, Sealer()).saveProfile("ana", "pw", "", biometric = false, remember = false, loggedIn = true, strong = true)
        assertFalse(saved.isStrongBiometric)
        assertEquals("", saved.sealedPassword)
        assertEquals("", saved.password)
    }
}
