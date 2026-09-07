// =============================================================================
// HYDRA-UMC CONTROL - Persistent storage for authentication credentials
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0 - see LICENSE
// =============================================================================
// The username/password/bearer token this class caches (only ever used to
// prefill the login form and to silently restore a session via the stored
// token - see RobotViewModel.kt's own init{}) are secrets that plain
// SharedPreferences/DataStore Preferences were never meant to hold: both
// store an unencrypted XML/protobuf file under /data/data/<pkg>/, readable
// in full on any rooted device or from an ADB backup. Backed instead by
// EncryptedSharedPreferences (androidx.security.crypto): AES256-GCM per
// value with a hardware Keystore-backed master key on any device that
// supports it (StrongBox/TEE), software Keystore fallback otherwise -
// either way, nothing plaintext ever touches disk.
package com.hydraumc.control.network

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Executors

// Real defensive isolation, from the same investigation as cachedPrefs
// below (see its own comment): a single dedicated thread instead of the
// shared Dispatchers.IO pool, so this class's own AndroidKeyStore/
// EncryptedSharedPreferences work is NEVER at the mercy of however much
// unrelated blocking I/O (Discovery.kt's own subnet scan launches up to
// SCAN_CONCURRENCY probes on Dispatchers.IO at the very same cold-start
// moment RobotViewModel's init{} needs this class) happens to be sharing
// that pool at the same time. One thread is enough - every call here goes
// through the same synchronized openEncryptedPrefs() gate below anyway,
// so there is never real parallel work to lose by not having more.
private val authPrefsDispatcher = Executors.newSingleThreadExecutor { r ->
    Thread(r, "AuthPrefs").apply { isDaemon = true }
}.asCoroutineDispatcher()

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
        // Best-effort cleanup: an unencrypted DataStore Preferences file at
        // this same path (context.filesDir/datastore/auth_prefs.preferences_pb)
        // could still hold this same username/password/token from an older
        // install of this app - leaving it on disk would defeat the point of
        // encrypting the store above. Safe to attempt on every construction:
        // delete() on an already-missing file is a harmless no-op.
        runCatching { File(context.filesDir, "datastore/auth_prefs.preferences_pb").delete() }
    }

    // Real bug this fixes, live-reproduced with timing logs: a bare
    // `authPrefs.loadAuth()` call on app cold start took 17.5 SECONDS -
    // traced to createEncryptedPrefs() below being called fresh on every
    // single loadAuth()/saveAuth()/clearAuth() invocation instead of once.
    // MasterKey.Builder(context).build() is a real AndroidKeyStore
    // round-trip (key generation or retrieval, StrongBox/TEE-backed on
    // devices that support it), not a cheap in-memory object - recreating
    // it on every call, rather than once per AuthPrefs instance (itself
    // already a singleton for this ViewModel's lifetime - see
    // RobotViewModel's own `private val authPrefs = AuthPrefs(application)`),
    // was paying that same cost repeatedly for no reason. Reported as "el
    // splash se queda negro mucho rato" - MainActivity's own splash-gating
    // logic was working correctly the whole time; it was faithfully
    // waiting on this real 17.5s stall.
    @Volatile private var cachedPrefs: SharedPreferences? = null

    private fun openEncryptedPrefs(): SharedPreferences {
        cachedPrefs?.let { return it }
        return synchronized(this) {
            cachedPrefs?.let { return@synchronized it }
            val prefs = openEncryptedPrefsUncached()
            cachedPrefs = prefs
            prefs
        }
    }

    private fun openEncryptedPrefsUncached(): SharedPreferences {
        return try {
            createEncryptedPrefs()
        } catch (e: Exception) {
            // Real crash reproduced live: AEADBadTagException /
            // KeyStoreException("Signature/MAC verification failed") thrown
            // from inside EncryptedSharedPreferences.create() itself (i.e.
            // decrypting the stored Tink keyset, before this class ever gets
            // a SharedPreferences instance back), crashing every screen that
            // touches auth on startup - including the login screen, since
            // RobotViewModel's init{} calls loadAuth() unconditionally.
            // Root cause: android:allowBackup="true" (AndroidManifest.xml)
            // with no exclusion rule lets Android's auto-backup restore this
            // *file's ciphertext* on a fresh install/reinstall, but the
            // Keystore-backed AES key it was encrypted with is
            // hardware-tied and never survives that restore - the freshly
            // generated key can never decrypt the restored blob, forever
            // (a real android:allowBackup gotcha with EncryptedSharedPreferences,
            // not theoretical - see AndroidManifest.xml/backup rules for the
            // matching exclusion this crash motivated). Whatever the exact
            // cause, an undecryptable keyset is unrecoverable in place - the
            // only way out is to delete the corrupted file(s) and start a
            // fresh keyset, which just means the user has to log in again
            // instead of the app crash-looping on every launch.
            Log.e("AuthPrefs", "Encrypted prefs unreadable, resetting: ${e.javaClass.simpleName}: ${e.message}")
            deleteCorruptedPrefs()
            // Live-reproduced: deleting only the SharedPreferences XML files
            // above was NOT enough - the retry threw the exact same
            // AEADBadTagException, proving the actual broken half is the
            // AndroidKeyStore key entry itself (MasterKey's default alias),
            // not the ciphertext on disk. MasterKey.Builder(context) resolves
            // that alias by name and reuses whatever key is already there if
            // one exists - so without deleting the Keystore entry too, the
            // "fresh" MasterKey it builds is the same broken key, and
            // EncryptedSharedPreferences.create() fails identically on retry.
            deleteCorruptedMasterKey()
            createEncryptedPrefs()
        }
    }

    private fun createEncryptedPrefs(): SharedPreferences {
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

    /**
     * Deletes this prefs file and Tink's own companion keyset file(s) it
     * stores alongside it (named `"$PREFS_FILE_NAME..."` - both start with
     * the same prefix) so the next [createEncryptedPrefs] call generates a
     * brand new keyset instead of tripping over the same undecryptable one.
     */
    private fun deleteCorruptedPrefs() {
        runCatching {
            val prefsDir = File(context.applicationInfo.dataDir, "shared_prefs")
            prefsDir.listFiles { f -> f.name.startsWith(PREFS_FILE_NAME) }?.forEach { it.delete() }
        }
    }

    /**
     * Deletes the AndroidKeyStore entry MasterKey.Builder resolves by alias
     * (reusing an existing key under that alias if one is already there,
     * which is exactly what made [deleteCorruptedPrefs] alone insufficient -
     * see the comment where this is called from).
     */
    private fun deleteCorruptedMasterKey() {
        runCatching {
            val keyStore = java.security.KeyStore.getInstance("AndroidKeyStore")
            keyStore.load(null)
            keyStore.deleteEntry(MasterKey.DEFAULT_MASTER_KEY_ALIAS)
        }
    }

    suspend fun loadAuth(): UserProfile = withContext(authPrefsDispatcher) {
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

    suspend fun saveAuth(profile: UserProfile) = withContext(authPrefsDispatcher) {
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

    suspend fun clearAuth() = withContext(authPrefsDispatcher) {
        // Clears the bearer token too, not just the "logged in" flag - without
        // this, connect()/setupWebSocket() (which read loadAuth().token
        // directly as a fallback, not gated on isLoggedIn) would keep
        // reusing the old session's token after an explicit logout, as if
        // the logout had never happened.
        openEncryptedPrefs().edit()
            .putBoolean(KEY_LOGGED_IN, false)
            .putString(KEY_TOKEN, "")
            .apply()
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
