// =============================================================================
// HYDRA-UMC CONTROL - The switch of the push messages of the server
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
// Off until the person turns it on. While it is on, the phone registers with the server of each signed-in session; it
// takes the registration back when the switch goes off and when the person signs out.
package com.hydraumc.control.viewmodel

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import com.hydraumc.control.network.HydraApiClient
import com.hydraumc.control.push.FirebasePush
import com.hydraumc.control.push.PushEndpoint
import com.hydraumc.control.push.PushRegistration
import com.hydraumc.control.push.PushResult
import com.hydraumc.control.push.PushTokenStore
import com.hydraumc.control.util.NotificationPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private class PreferencesTokenStore(context: Context) : PushTokenStore {
    private val preferences = context.applicationContext.getSharedPreferences("hydra_push", Context.MODE_PRIVATE)
    override fun load(server: String): String? = preferences.getString(PREFIX + server, null)
    override fun save(server: String, token: String?) {
        preferences.edit().apply { if (token == null) remove(PREFIX + server) else putString(PREFIX + server, token) }.apply()
    }
    override fun servers(): Set<String> = preferences.all.keys.filter { it.startsWith(PREFIX) }.map { it.removePrefix(PREFIX) }.toSet()
    override fun clear() = preferences.edit().clear().apply()

    private companion object {
        const val PREFIX = "token|"
    }
}

class PushSwitchController(
    context: Context,
    private val scope: CoroutineScope,
    private val preferences: NotificationPrefs,
    private val session: () -> HydraApiClient?,
    private val registration: PushRegistration = PushRegistration(FirebasePush(context), PreferencesTokenStore(context)),
) {
    private val app = context.applicationContext
    val enabled = mutableStateOf(false)
    /** How the last attempt ended; null before one and after the switch goes off. */
    val result = mutableStateOf<PushResult?>(null)
    val available: Boolean get() = registration.available
    @Volatile private var touched = false

    fun load() {
        scope.launch {
            val saved = preferences.isPushEnabled()
            if (!touched) enabled.value = saved
            sync()
        }
    }

    fun set(on: Boolean) {
        touched = true
        enabled.value = on && available
        result.value = null
        val current = session()
        val server = endpoint()
        scope.launch {
            preferences.setPushEnabled(enabled.value)
            if (enabled.value) sync() else registration.removeAll(server, current?.serverId)
        }
    }

    /** Tells the server of the signed-in session, when the switch is on. */
    fun sync() {
        if (!enabled.value || !available) return
        val current = session() ?: return
        val server = endpoint() ?: return
        val language = app.resources.configuration.locales[0].language
        scope.launch { result.value = registration.register(server, current.serverId, language) }
    }

    /** The session of the server [serverId] ends: its registration is taken back. [client] is the one of that session, if there still is one. */
    fun signedOut(client: HydraApiClient?, serverId: String?) {
        result.value = null
        if (serverId == null) return
        val server = client?.takeIf { !it.authToken.isNullOrEmpty() && it.serverId == serverId }?.asPushEndpoint()
        scope.launch { registration.remove(server, serverId) }
    }

    /** Another server is in use now: how the last attempt ended was about the one before. */
    fun serverChanged() {
        result.value = null
    }

    /** A kept server was removed without a session to tell it: the token of the phone is dropped, and the one in use registers again. */
    fun serverForgotten(serverId: String) {
        scope.launch {
            registration.remove(null, serverId)
            sync()
        }
    }

    private fun endpoint(): PushEndpoint? = session()?.takeIf { !it.authToken.isNullOrEmpty() }?.asPushEndpoint()

    private fun HydraApiClient.asPushEndpoint(): PushEndpoint {
        val client = this
        return object : PushEndpoint {
            override suspend fun registerPush(token: String, locale: String) = client.postPushDevice(token, locale)
            override suspend fun removePush(token: String) = client.deletePushDevice(token)
        }
    }
}
