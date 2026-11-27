// =============================================================================
// HYDRA-UMC CONTROL - Receives the push messages of the server
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
// The messages only inform. With the app out of sight Android shows them by itself on the channel the server names;
// with the app on screen this service shows the same title and sentence. A tap opens the app and nothing else: no
// message carries an action, and nothing of its content is ever read as a command.
package com.hydraumc.control.push

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.hydraumc.control.util.NotificationHelper

class HydraPushService : FirebaseMessagingService() {
    override fun onCreate() {
        FirebasePush.start(this)
        super.onCreate()
    }

    /** Firebase changed the token. The app tells the server the next time it connects with a signed-in session. */
    override fun onNewToken(token: String) = Unit

    override fun onMessageReceived(message: RemoteMessage) {
        val shown = message.notification ?: return
        val title = shown.title?.takeIf { it.isNotBlank() } ?: return
        com.hydraumc.control.util.NoticeLog(this).add(
            com.hydraumc.control.util.NoticeKind.SERVER_MESSAGE,
            listOf(title, shown.body.orEmpty()).filter { it.isNotBlank() }.joinToString(" - "),
        )
        NotificationHelper.createChannel(this)
        NotificationHelper.showServerNotice(this, title, shown.body.orEmpty(), shown.tag ?: message.data["kind"].orEmpty())
    }
}
