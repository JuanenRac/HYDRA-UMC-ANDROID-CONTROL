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
private val KEY_REMEMBER_ME = booleanPreferencesKey("remember_me")
private val KEY_LOGGED_IN = booleanPreferencesKey("is_logged_in")

/**
 * Manages user authentication persistence.
 */
class AuthPrefs(private val context: Context) {
    suspend fun loadAuth(): Triple<String?, Boolean, Boolean> {
        val prefs = context.authDataStore.data.first()
        return Triple(
            prefs[KEY_USERNAME],
            prefs[KEY_REMEMBER_ME] ?: false,
            prefs[KEY_LOGGED_IN] ?: false
        )
    }

    suspend fun saveAuth(username: String, rememberMe: Boolean, isLoggedIn: Boolean) {
        context.authDataStore.edit { prefs ->
            prefs[KEY_USERNAME] = username
            prefs[KEY_REMEMBER_ME] = rememberMe
            prefs[KEY_LOGGED_IN] = isLoggedIn
        }
    }

    suspend fun clearAuth() {
        context.authDataStore.edit { prefs ->
            prefs[KEY_LOGGED_IN] = false
            // Keep username if remember me was on, or clear all? 
            // Standard: keep username, clear session.
        }
    }
}
