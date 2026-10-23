// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - Persistent storage of the TLS settings
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.network

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.tlsDataStore by preferencesDataStore(name = "tls_prefs")
private val KEY_MODE = stringPreferencesKey("mode")
private val KEY_PIN = stringPreferencesKey("pinned_fingerprint")
private val KEY_CA = stringPreferencesKey("custom_ca_pem")
private val KEY_PLAIN_REMOTE = booleanPreferencesKey("allow_plain_remote")

/** Loads and saves [TlsSettings]. Nothing stored means the defaults: [TlsMode.AUTO], no fingerprint, no CA. */
class TlsPrefs(private val context: Context) {
    suspend fun load(): TlsSettings {
        val prefs = context.tlsDataStore.data.first()
        return TlsSettings(
            mode = TlsMode.entries.firstOrNull { it.name == prefs[KEY_MODE] } ?: TlsMode.AUTO,
            pinnedFingerprint = prefs[KEY_PIN].orEmpty(),
            customCaPem = prefs[KEY_CA].orEmpty(),
            allowPlainRemote = prefs[KEY_PLAIN_REMOTE] ?: false,
        )
    }

    suspend fun save(settings: TlsSettings) {
        context.tlsDataStore.edit { prefs ->
            prefs[KEY_MODE] = settings.mode.name
            prefs[KEY_PIN] = settings.pinnedFingerprint
            prefs[KEY_CA] = settings.customCaPem
            prefs[KEY_PLAIN_REMOTE] = settings.allowPlainRemote
        }
    }
}
