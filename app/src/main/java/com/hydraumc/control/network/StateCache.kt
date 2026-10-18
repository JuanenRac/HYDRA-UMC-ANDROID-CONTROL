// =============================================================================
// HYDRA-UMC CONTROL - Persistent cache for the last known system state
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0 - see LICENSE
// =============================================================================
package com.hydraumc.control.network

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import org.json.JSONObject

private val Context.cacheDataStore by preferencesDataStore(name = "state_cache")
private val KEY_LAST_STATE = stringPreferencesKey("last_known_state")

/**
 * Manages persistence of the last known HydraState for offline viewing.
 */
class StateCache(private val context: Context) {

    /**
     * Saves the state to disk. Takes the already-serialized text, not the live
     * [JSONObject]: the caller serializes on the main thread (where the tree
     * is mutated by WebSocket deltas and optimistic commands) because org.json
     * is not thread-safe, and the DataStore lambda below runs on another one.
     * The camera credentials an administrator's tree carries are dropped
     * first - the cache has no use for them.
     */
    suspend fun saveState(serializedState: String) {
        val sanitized = redactSecrets(serializedState)
        context.cacheDataStore.edit { prefs ->
            prefs[KEY_LAST_STATE] = sanitized
        }
    }

    companion object {
        /** Keys of the settings tree that hold credentials and never go to disk. */
        private val SECRET_KEYS = setOf("ipUsername", "ipPassword")

        /** [serializedState] without any [SECRET_KEYS] at any depth; an unreadable text is returned as it came. */
        fun redactSecrets(serializedState: String): String {
            val root = try {
                JSONObject(serializedState)
            } catch (_: Exception) {
                return serializedState
            }
            strip(root)
            return root.toString()
        }

        private fun strip(value: Any?) {
            when (value) {
                is JSONObject -> {
                    SECRET_KEYS.forEach { value.remove(it) }
                    val keys = value.keys().asSequence().toList()
                    keys.forEach { strip(value.opt(it)) }
                }
                is org.json.JSONArray -> for (i in 0 until value.length()) strip(value.opt(i))
            }
        }
    }

    /** Loads the last known state from disk. */
    suspend fun loadState(): JSONObject? {
        val prefs = context.cacheDataStore.data.first()
        val jsonStr = prefs[KEY_LAST_STATE] ?: return null
        return try {
            JSONObject(jsonStr)
        } catch (e: Exception) {
            null
        }
    }
}
