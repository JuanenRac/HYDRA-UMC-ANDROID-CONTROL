// =============================================================================
// HYDRA-UMC CONTROL - Persistent storage for authentication using DataStore
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0 - see LICENSE
// =============================================================================
package com.hydraumc.control.network

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

/** Extension property for authentication DataStore. */
private val Context.authDataStore by preferencesDataStore(name = "auth_prefs")
private val KEY_USERNAME = stringPreferencesKey("username")
private val KEY_PASSWORD = stringPreferencesKey("password")
private val KEY_EMAIL = stringPreferencesKey("email")
private val KEY_REMEMBER_ME = booleanPreferencesKey("remember_me")
private val KEY_LOGGED_IN = booleanPreferencesKey("is_logged_in")

/**
 * Manages user authentication and profile persistence.
 */
class AuthPrefs(private val context: Context) {
    suspend fun loadAuth(): UserProfile {
        val prefs = context.authDataStore.data.first()
        return UserProfile(
            username = prefs[KEY_USERNAME] ?: "",
            password = prefs[KEY_PASSWORD] ?: "",
            email = prefs[KEY_EMAIL] ?: "",
            rememberMe = prefs[KEY_REMEMBER_ME] ?: false,
            isLoggedIn = prefs[KEY_LOGGED_IN] ?: false
        )
    }

    suspend fun saveAuth(profile: UserProfile) {
        context.authDataStore.edit { prefs ->
            prefs[KEY_USERNAME] = profile.username
            prefs[KEY_PASSWORD] = profile.password
            prefs[KEY_EMAIL] = profile.email
            prefs[KEY_REMEMBER_ME] = profile.rememberMe
            prefs[KEY_LOGGED_IN] = profile.isLoggedIn
        }
    }

    suspend fun clearAuth() {
        context.authDataStore.edit { prefs ->
            prefs[KEY_LOGGED_IN] = false
        }
    }
}

/** 
 * Data class representing the user's profile and session state.
 */
data class UserProfile(
    val username: String,
    val password: String,
    val email: String,
    val rememberMe: Boolean,
    val isLoggedIn: Boolean
)
