// =============================================================================
// HYDRA-UMC CONTROL - Persistent storage for authentication credentials
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0 - see LICENSE
// =============================================================================
// Was plain Jetpack DataStore Preferences until 2026-08-19 - functionally the
// same problem as plain SharedPreferences (an unencrypted XML/protobuf file
// under /data/data/<pkg>/, readable in full on any rooted device or from an
// ADB backup) despite being the newer API. The username/password/bearer
// token this class caches (only ever used to prefill the login form and to
// silently restore a session via the stored token - see
// RobotViewModel.kt's own init{}) are exactly the kind of secret that file
// format was never meant to hold. Now backed by EncryptedSharedPreferences
// (androidx.security.crypto) instead: AES256-GCM per value with a hardware
// Keystore-backed master key on any device that supports it (StrongBox/TEE),
// software Keystore fallback otherwise - either way, nothing plaintext ever
// touches disk.
package com.hydraumc.control.network

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private const val PREFS_FILE_NAME = "auth_prefs_encrypted"
private const val KEY_USERNAME = "username"
private const val KEY_PASSWORD = "password"
private const val KEY_EMAIL = "email"
private const val KEY_REMEMBER_ME = "remember_me"
private const val KEY_LOGGED_IN = "is_logged_in"
private const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"
private const val KEY_TOKEN = "token"

/**
 * Manages user authentication and profile persistence, encrypted at rest.
 */
class AuthPrefs(private val context: Context) {

    init {
        // One-time best-effort cleanup: the old plaintext DataStore file
        // (context.filesDir/datastore/auth_prefs.preferences_pb) held this
        // same username/password/token before the 2026-08-19 encryption
        // migration above - leaving it on disk would defeat the point of
        // encrypting the new store. Safe to attempt on every construction:
        // delete() on an already-missing file is a harmless no-op.
        runCatching { File(context.filesDir, "datastore/auth_prefs.preferences_pb").delete() }
    }

    private fun openEncryptedPrefs(): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            PREFS_FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    suspend fun loadAuth(): UserProfile = withContext(Dispatchers.IO) {
        val prefs = openEncryptedPrefs()
        UserProfile(
            username = prefs.getString(KEY_USERNAME, "") ?: "",
            password = prefs.getString(KEY_PASSWORD, "") ?: "",
            email = prefs.getString(KEY_EMAIL, "") ?: "",
            rememberMe = prefs.getBoolean(KEY_REMEMBER_ME, false),
            isLoggedIn = prefs.getBoolean(KEY_LOGGED_IN, false),
            isBiometricEnabled = prefs.getBoolean(KEY_BIOMETRIC_ENABLED, false),
            token = prefs.getString(KEY_TOKEN, "") ?: "",
        )
    }

    suspend fun saveAuth(profile: UserProfile) = withContext(Dispatchers.IO) {
        openEncryptedPrefs().edit()
            .putString(KEY_USERNAME, profile.username)
            .putString(KEY_PASSWORD, profile.password)
            .putString(KEY_EMAIL, profile.email)
            .putBoolean(KEY_REMEMBER_ME, profile.rememberMe)
            .putBoolean(KEY_LOGGED_IN, profile.isLoggedIn)
            .putBoolean(KEY_BIOMETRIC_ENABLED, profile.isBiometricEnabled)
            .putString(KEY_TOKEN, profile.token)
            .apply()
        Unit
    }

    suspend fun clearAuth() = withContext(Dispatchers.IO) {
        openEncryptedPrefs().edit().putBoolean(KEY_LOGGED_IN, false).apply()
        Unit
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
    val isLoggedIn: Boolean,
    val isBiometricEnabled: Boolean = false,
    val token: String = ""
)
