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
    
    /** Saves the full JSON state to disk. */
    suspend fun saveState(state: JSONObject) {
        context.cacheDataStore.edit { prefs ->
            prefs[KEY_LAST_STATE] = state.toString()
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
