// =============================================================================
// HYDRA-UMC CONTROL - The report "Share diagnostic" prepares
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
// Only what describes the app and the phone. No address, user name, password, token or robot data can enter it: the
// function takes none of them.
// =============================================================================
package com.hydraumc.control.util

import android.content.Context
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import com.hydraumc.control.BuildConfig
import java.util.Locale

object DiagnosticReport {
    fun render(
        appVersion: String, appCode: Int, androidRelease: String, sdk: Int, device: String, language: String, notificationsAllowed: Boolean,
    ): String = buildString {
        appendLine("HYDRA-UMC CONTROL (Android)")
        appendLine("app: $appVersion ($appCode)")
        appendLine("android: $androidRelease (API $sdk)")
        appendLine("device: $device")
        appendLine("language: $language")
        appendLine("notifications allowed: ${if (notificationsAllowed) "yes" else "no"}")
    }

    fun ofThisPhone(context: Context): String = render(
        appVersion = BuildConfig.VERSION_NAME,
        appCode = BuildConfig.VERSION_CODE,
        androidRelease = Build.VERSION.RELEASE.orEmpty(),
        sdk = Build.VERSION.SDK_INT,
        device = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
        language = Locale.getDefault().toLanguageTag(),
        notificationsAllowed = NotificationManagerCompat.from(context).areNotificationsEnabled(),
    )
}
