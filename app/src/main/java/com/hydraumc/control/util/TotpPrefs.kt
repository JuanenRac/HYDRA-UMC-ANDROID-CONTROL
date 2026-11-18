// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - util/TotpPrefs.kt
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
// The one switch of the second-factor screen: keep it out of screenshots while a key or recovery codes are shown. On by default (the conservative
// choice); the person can turn it off, for instance to take a note of the codes with a screenshot. Nothing secret is stored here, only the switch.
package com.hydraumc.control.util

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.totpPrefsDataStore by preferencesDataStore(name = "totp_prefs")
private val KEY_BLOCK_CAPTURES = booleanPreferencesKey("block_captures")

/** Loads and saves the switch that keeps the second-factor screen out of screenshots and screen recordings. */
class TotpPrefs(private val context: Context) {
    /** True when the key and the codes are kept out of captures; true when nothing was ever saved. */
    suspend fun blockCaptures(): Boolean = context.totpPrefsDataStore.data.first()[KEY_BLOCK_CAPTURES] ?: DEFAULT_BLOCK_CAPTURES

    suspend fun setBlockCaptures(on: Boolean) {
        context.totpPrefsDataStore.edit { it[KEY_BLOCK_CAPTURES] = on }
    }

    companion object {
        /** The conservative default. */
        const val DEFAULT_BLOCK_CAPTURES = true
    }
}
