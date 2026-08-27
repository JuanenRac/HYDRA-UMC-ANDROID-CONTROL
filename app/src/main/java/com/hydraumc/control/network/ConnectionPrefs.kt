// =============================================================================
// HYDRA-UMC CONTROL - Persistent storage for connection settings using DataStore
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0 - see LICENSE
// =============================================================================
package com.hydraumc.control.network

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

/** Extension property to provide a DataStore instance for connection preferences. */
private val Context.connectionDataStore by preferencesDataStore(name = "connection_prefs")
/** Preference key for the saved IP address. */
private val KEY_IP = stringPreferencesKey("ip_address")
/** Preference key for the saved port number. */
private val KEY_PORT = stringPreferencesKey("port")

/**
 * Helper class to manage loading and saving network connection settings.
 * @property context The application context needed for DataStore access.
 */
class ConnectionPrefs(private val context: Context) {
    /** 
     * Loads the saved IP and Port from persistent storage. 
     * @return A Pair containing (IP, Port), or null if none are saved.
     */
    suspend fun load(): Pair<String, String>? {
        val prefs = context.connectionDataStore.data.first()
        val ip = prefs[KEY_IP] ?: return null
        val port = prefs[KEY_PORT] ?: return null
        return ip to port
    }

    /** 
     * Saves the provided IP and Port to persistent storage. 
     * @param ip The IP address string.
     * @param port The port number string.
     */
    suspend fun save(ip: String, port: String) {
        context.connectionDataStore.edit { prefs ->
            prefs[KEY_IP] = ip
            prefs[KEY_PORT] = port
        }
    }
}
