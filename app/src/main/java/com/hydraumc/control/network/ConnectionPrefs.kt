// =============================================================================
// HYDRA-UMC CONTROL - Persistent storage for connection settings using DataStore
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

/** Extension property to provide a DataStore instance for connection preferences. */
private val Context.connectionDataStore by preferencesDataStore(name = "connection_prefs")
/** Preference key for the saved IP address. */
private val KEY_IP = stringPreferencesKey("ip_address")
/** Preference key for the saved port number. */
private val KEY_PORT = stringPreferencesKey("port")
/** Preference key for the controller id of this installation (see SessionController). */
private val KEY_CONTROLLER_ID = stringPreferencesKey("controller_id")
/** Preference key for the conditional-writes mode (off, standard, strict). */
private val KEY_CONDITIONAL_MODE = stringPreferencesKey("conditional_mode")
/** Preference key for asking before PLAY starts a trajectory. */
private val KEY_CONFIRM_PLAY = booleanPreferencesKey("confirm_play")
/** Preference key for Listen only (off until the person turns it on). */
private val KEY_LISTEN_ONLY = booleanPreferencesKey("listen_only")
/** Preference key for the HOME/RESET switch (off until the person turns it on). */
private val KEY_HOME_RESET = booleanPreferencesKey("home_reset_enabled")

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

    /** The conditional-writes mode chosen in this app (see [ConditionalMode]); the standard one before any choice. */
    suspend fun loadConditionalMode(): ConditionalMode =
        ConditionalMode.fromKey(context.connectionDataStore.data.first()[KEY_CONDITIONAL_MODE])

    suspend fun saveConditionalMode(mode: ConditionalMode) {
        context.connectionDataStore.edit { prefs -> prefs[KEY_CONDITIONAL_MODE] = mode.key }
    }

    /** Whether PLAY asks first (the robot, its points, the speed and who reserved it); yes before any choice. */
    suspend fun loadConfirmPlay(): Boolean = context.connectionDataStore.data.first()[KEY_CONFIRM_PLAY] ?: true

    suspend fun saveConfirmPlay(ask: Boolean) {
        context.connectionDataStore.edit { prefs -> prefs[KEY_CONFIRM_PLAY] = ask }
    }

    /** Whether Listen only is on (no command but STOP and the safe-mode disable); off before any choice. */
    suspend fun loadListenOnly(): Boolean = context.connectionDataStore.data.first()[KEY_LISTEN_ONLY] ?: false

    suspend fun saveListenOnly(on: Boolean) {
        context.connectionDataStore.edit { prefs -> prefs[KEY_LISTEN_ONLY] = on }
    }

    /** Whether HOME/RESET is switched on; [default] (off in the contract) before any choice. */
    suspend fun loadHomeReset(default: Boolean): Boolean = context.connectionDataStore.data.first()[KEY_HOME_RESET] ?: default

    /**
     * A switch of this app by its [name] (one of [Flags]); [default] before any choice. The defaults live in the
     * command contract, so none is decided here.
     */
    suspend fun loadFlag(name: String, default: Boolean): Boolean =
        context.connectionDataStore.data.first()[booleanPreferencesKey("flag_$name")] ?: default

    suspend fun saveFlag(name: String, on: Boolean) {
        context.connectionDataStore.edit { prefs -> prefs[booleanPreferencesKey("flag_$name")] = on }
    }

    /** The names of the switches kept with [loadFlag] and [saveFlag]. */
    object Flags {
        const val DEAD_MAN_JOG = "dead_man_jog"
        const val LINK_BLOCK_JOG = "link_block_jog"
        const val LINK_INDICATOR = "link_indicator"
        const val FILTER_OBSCURED_TOUCHES = "filter_obscured_touches"
        const val PAIRING = "pairing_links"
        const val WATCH_ALERTS = "watch_alerts"
        const val PRESENCE_RELAY = "presence_relay"
        const val VOICE_CONFIRM_RELAY = "voice_confirm_relay"
    }

    suspend fun saveHomeReset(on: Boolean) {
        context.connectionDataStore.edit { prefs -> prefs[KEY_HOME_RESET] = on }
    }

    /** The controller id kept for this installation, or null before the first one was saved. */
    suspend fun loadControllerId(): String? = context.connectionDataStore.data.first()[KEY_CONTROLLER_ID]

    /** Keeps the controller id, so a reservation made from this phone can still be released after the app restarts. */
    suspend fun saveControllerId(id: String) {
        context.connectionDataStore.edit { prefs -> prefs[KEY_CONTROLLER_ID] = id }
    }
}
