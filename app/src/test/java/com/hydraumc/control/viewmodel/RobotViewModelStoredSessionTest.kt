// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - app/src/test/java/com/hydraumc/control/viewmodel/RobotViewModelStoredSessionTest.kt
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.viewmodel

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.hydraumc.control.network.AuthStore
import com.hydraumc.control.network.StopAllStatus
import com.hydraumc.control.network.UserProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The view model with a stored login handed in from outside, so it runs on the JVM without an Android keystore: the profile the person saves keeps the
 * tokens of the stored session, a store that cannot be read starts with no login, and the stop works without anything stored.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RobotViewModelStoredSessionTest {

    private class Store(var profile: UserProfile, private val failToLoad: Boolean = false) : AuthStore {
        val saves = mutableListOf<UserProfile>()
        override suspend fun loadAuth(): UserProfile {
            if (failToLoad) throw IllegalStateException("the keystore cannot be read")
            return profile
        }

        override suspend fun saveAuth(profile: UserProfile) {
            saves += profile
            this.profile = profile
        }
    }

    private val signedOut = UserProfile("", "", "", rememberMe = false, isLoggedIn = false, token = "access-token-1", refreshToken = "refresh-token-1")

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(store: AuthStore) = RobotViewModel(ApplicationProvider.getApplicationContext<Application>(), store)

    private fun waitUntil(timeoutMs: Long = 5_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!condition() && System.currentTimeMillis() < deadline) Thread.sleep(20)
        assertTrue("condition not met within ${timeoutMs}ms", condition())
    }

    @Test
    fun savingTheProfileThroughTheViewModelKeepsTheStoredTokens() {
        val store = Store(signedOut)
        val model = viewModel(store)

        model.saveUserProfile("operator", "a-password", "operator@example.com", biometric = false)
        waitUntil { store.saves.isNotEmpty() }

        val saved = store.saves.last()
        assertEquals("operator", saved.username)
        assertEquals("operator@example.com", saved.email)
        assertEquals("access-token-1", saved.token)
        assertEquals("refresh-token-1", saved.refreshToken)
    }

    @Test
    fun theFieldsOfTheFormAreTheOnesTheViewModelHolds() {
        val model = viewModel(Store(signedOut))

        model.saveUserProfile("operator", "a-password", "operator@example.com", biometric = true)

        assertEquals("operator", model.loginUsername.value)
        assertEquals("operator@example.com", model.loginEmail.value)
        assertTrue(model.isBiometricEnabled.value)
    }

    @Test
    fun aStoreThatCannotBeReadStartsWithNoLoginAndDoesNotCrash() {
        val model = viewModel(Store(signedOut, failToLoad = true))

        Thread.sleep(300)

        assertFalse(model.isLoggedIn.value)
    }

    @Test
    fun theViewModelDoesNotWriteToTheStoreUntilThePersonSavesSomething() {
        val store = Store(signedOut)
        viewModel(store)

        Thread.sleep(300)

        assertTrue("nothing should have been saved yet: ${store.saves}", store.saves.isEmpty())
    }

    @Test
    fun theGlobalStopOfAViewModelWithNoServerSaysItWasNotConfirmed() {
        val model = viewModel(Store(signedOut))

        model.globalStop()

        waitUntil { model.stopAllStatus.value is StopAllStatus.NotConfirmed }
        assertNotNull(model.stopAllStatus.value)
    }
}
