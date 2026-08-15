// =============================================================================
// HYDRA-UMC Android Control - network/ConnectionPrefs.kt
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0 - see LICENSE
//
// Persists the last IP/port the user connected to (Preferences DataStore, a
// dependency the project already declared in app/build.gradle.kts but never
// actually used until now) so the app doesn't forget it on every restart -
// small quality-of-life fix, not part of the REMOTE_API.md contract itself.
// =============================================================================
package com.hydraumc.control.network

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.connectionDataStore by preferencesDataStore(name = "connection_prefs")
private val KEY_IP = stringPreferencesKey("ip_address")
private val KEY_PORT = stringPreferencesKey("port")

class ConnectionPrefs(private val context: Context) {
    suspend fun load(): Pair<String, String>? {
        val prefs = context.connectionDataStore.data.first()
        val ip = prefs[KEY_IP] ?: return null
        val port = prefs[KEY_PORT] ?: return null
        return ip to port
    }

    suspend fun save(ip: String, port: String) {
        context.connectionDataStore.edit { prefs ->
            prefs[KEY_IP] = ip
            prefs[KEY_PORT] = port
        }
    }
}
