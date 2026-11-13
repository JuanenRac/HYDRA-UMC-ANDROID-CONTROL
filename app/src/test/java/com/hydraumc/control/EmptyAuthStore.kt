// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - app/src/test/java/com/hydraumc/control/EmptyAuthStore.kt
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control

import com.hydraumc.control.network.AuthStore
import com.hydraumc.control.network.UserProfile

/**
 * The stored login of a view model in a unit test: nothing stored. The view model reads its store in the background when it is created;
 * with the real store that read goes to the keystore, which a JVM test does not have, and its failure can land in the next test of the
 * same run ("uncaught exceptions before the test started"). A store that answers at once leaves nothing behind.
 */
object EmptyAuthStore : AuthStore {
    override suspend fun loadAuth() = UserProfile("", "", "", rememberMe = false, isLoggedIn = false)
    override suspend fun saveAuth(profile: UserProfile) {}
}
