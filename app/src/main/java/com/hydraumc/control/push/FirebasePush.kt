// =============================================================================
// HYDRA-UMC CONTROL - Push messages through Firebase Cloud Messaging
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
// Push is on only in a build made with the Firebase configuration file of the installation (it is NOT kept in the
// repository; see docs/PUSH_NOTIFICATIONS.md): the build then holds the four values Firebase needs and the app starts
// Firebase itself from them. Without them [FirebasePush.available] is false and nothing here does anything.
package com.hydraumc.control.push

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.hydraumc.control.BuildConfig
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/** What the app needs from the push service of the phone. The tests plug in their own. */
interface PushPlatform {
    /** Whether this build can receive push messages at all. */
    val available: Boolean

    /** The token of this phone, or null when it could not be had. */
    suspend fun token(): String?

    /** Drops the token at the push service: whatever a server still sends to it finds no one. */
    fun forget()
}

class FirebasePush(context: Context) : PushPlatform {
    private val app = context.applicationContext

    override val available: Boolean get() = configured

    override suspend fun token(): String? {
        if (!start(app)) return null
        return withTimeoutOrNull(TOKEN_TIMEOUT_MS) {
            suspendCancellableCoroutine { waiting ->
                runCatching {
                    FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                        if (waiting.isActive) waiting.resume(if (task.isSuccessful) task.result?.takeIf { it.isNotBlank() } else null)
                    }
                }.onFailure { if (waiting.isActive) waiting.resume(null) }
            }
        }
    }

    override fun forget() {
        if (!start(app)) return
        runCatching { FirebaseMessaging.getInstance().deleteToken() }
    }

    companion object {
        private const val TOKEN_TIMEOUT_MS = 15_000L

        val configured: Boolean
            get() = BuildConfig.FCM_APP_ID.isNotBlank() && BuildConfig.FCM_API_KEY.isNotBlank() && BuildConfig.FCM_PROJECT_ID.isNotBlank()

        /** Starts Firebase once. False when this build has no Firebase configuration. */
        fun start(context: Context): Boolean {
            if (!configured) return false
            return runCatching {
                if (FirebaseApp.getApps(context).isEmpty()) {
                    FirebaseApp.initializeApp(
                        context.applicationContext,
                        FirebaseOptions.Builder()
                            .setApplicationId(BuildConfig.FCM_APP_ID)
                            .setApiKey(BuildConfig.FCM_API_KEY)
                            .setProjectId(BuildConfig.FCM_PROJECT_ID)
                            .setGcmSenderId(BuildConfig.FCM_SENDER_ID)
                            .build(),
                    )
                }
                true
            }.getOrDefault(false)
        }
    }
}
