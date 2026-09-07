// =============================================================================
// HYDRA-UMC CONTROL - Persistent storage for the in-app notifications toggle
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0 - see LICENSE
// =============================================================================
// Separate from Android's own per-app system notification permission/toggle
// (Settings > Apps > HYDRA-UMC Control > Notifications) - this is an
// ADDITIONAL, in-app preference the owner asked for directly ("una opción
// activable en settings dentro de la app"), so they don't have to leave the
// app to quiet the persistent safety notification (NotificationHelper's own
// showSafetyNotification(), shown for as long as a WebSocket connection
// stays open) or ad-hoc mission alerts. Turning the OS permission off still
// wins either way (Android silently drops the notification regardless of
// this flag) - this only ever narrows what gets shown, never widens it.
package com.hydraumc.control.util

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.notificationPrefsDataStore by preferencesDataStore(name = "notification_prefs")
private val KEY_NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")

/**
 * Helper class to manage loading and saving the in-app notifications toggle.
 * @property context The application context needed for DataStore access.
 */
class NotificationPrefs(private val context: Context) {
    /** Loads whether notifications are enabled - defaults to true, matching this app's always-on behavior before this preference existed. */
    suspend fun isEnabled(): Boolean {
        return context.notificationPrefsDataStore.data.first()[KEY_NOTIFICATIONS_ENABLED] ?: true
    }

    /** Persists the notifications-enabled preference. */
    suspend fun setEnabled(enabled: Boolean) {
        context.notificationPrefsDataStore.edit { prefs ->
            prefs[KEY_NOTIFICATIONS_ENABLED] = enabled
        }
    }
}
