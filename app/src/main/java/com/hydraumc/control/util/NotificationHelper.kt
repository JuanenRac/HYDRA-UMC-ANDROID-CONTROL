// =============================================================================
// HYDRA-UMC CONTROL - Helper for industrial notifications and alerts
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.hydraumc.control.MainActivity
import com.hydraumc.control.R

/**
 * The global stop that the notification, the home screen widget and the quick settings tile ask MainActivity for.
 * MainActivity is exported (it is the launcher), so any app can send it an intent with this action. Only an intent that
 * carries the random value kept in the private storage of this app is obeyed.
 */
object GlobalStopRequest {
    const val ACTION = "ACTION_GLOBAL_ESTOP"
    private const val EXTRA = "com.hydraumc.control.extra.STOP_PROOF"
    private const val PREFS = "hydra_stop_request"
    private const val KEY = "proof"

    @Synchronized
    private fun proof(context: Context): String {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.getString(KEY, null)?.let { return it }
        val bytes = ByteArray(32).also { java.security.SecureRandom().nextBytes(it) }
        val made = bytes.joinToString("") { "%02x".format(it) }
        prefs.edit().putString(KEY, made).commit()
        return made
    }

    /** Makes [intent] a global stop request of this app. */
    fun stamp(context: Context, intent: Intent): Intent = intent.setAction(ACTION).putExtra(EXTRA, proof(context))

    /** True only for a request that this app made itself. */
    fun isOurs(context: Context, intent: Intent?): Boolean {
        if (intent?.action != ACTION) return false
        val given = intent.getStringExtra(EXTRA) ?: return false
        return java.security.MessageDigest.isEqual(given.toByteArray(), proof(context).toByteArray())
    }
}

/**
 * Manages system notifications for robotic events.
 */
object NotificationHelper {
    private const val CHANNEL_ID = "hydra_mission_alerts"
    private const val ESTOP_CHANNEL_ID = "hydra_safety_alerts"
    private const val PERSISTENT_ID = 1001
    /** The channel the server names in its push messages (FCM_ANDROID_CHANNEL of HYDRA-UMC-SERVER). */
    private const val SERVER_CHANNEL_ID = "hydra_umc_server"

    // Distinct PendingIntent request codes for sendAlert()'s "open app" intent
    // vs showSafetyNotification()'s own "open app" intent. Both target
    // MainActivity with no action/data/categories set, so PendingIntent's own
    // filterEquals() considers them identical regardless of the differing
    // Intent.flags each call actually sets (flags/extras aren't part of that
    // comparison) - reusing requestCode 0 for both meant whichever one was
    // created FIRST silently "won": the OS returns/reuses that original
    // Intent (with its own flags) for every later PendingIntent.getActivity()
    // call sharing the same requestCode+filter, discarding the second call's
    // Intent entirely. FLAG_UPDATE_CURRENT closes the same gap for any future
    // caller that reuses one of these codes without realizing it collides.
    private const val REQUEST_CODE_ALERT_OPEN = 100
    private const val REQUEST_CODE_SAFETY_OPEN = 200
    private const val REQUEST_CODE_SAFETY_ESTOP = 201
    private const val REQUEST_CODE_SERVER_OPEN = 300

    /** Initializes the notification channels. */
    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // Mission Alerts Channel
            val missionChannel = NotificationChannel(CHANNEL_ID, "Mission Alerts", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Notifications for robot job status and hardware alerts"
            }
            notificationManager.createNotificationChannel(missionChannel)

            // Safety Channel (Persistent E-STOP)
            val safetyChannel = NotificationChannel(ESTOP_CHANNEL_ID, "Safety Controls", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Always-on safety controls for quick E-STOP access"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(safetyChannel)

            // The push messages of the server: information only.
            val serverChannel = NotificationChannel(SERVER_CHANNEL_ID, context.getString(R.string.push_channel_name), NotificationManager.IMPORTANCE_HIGH)
            notificationManager.createNotificationChannel(serverChannel)
        }
    }

    /** Sends a high-priority alert notification. */
    fun sendAlert(context: Context, title: String, message: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            REQUEST_CODE_ALERT_OPEN,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        val notificationManager: NotificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(System.currentTimeMillis().toInt(), builder.build())
    }

    /**
     * A push message of the server that arrived with the app on screen. It only informs: no action, and a tap just brings the
     * app forward without restarting its screens.
     */
    fun showServerNotice(context: Context, title: String, message: String, tag: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            REQUEST_CODE_SERVER_OPEN,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(context, SERVER_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
        val notificationManager: NotificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(tag, 0, builder.build())
    }

    /** Shows a persistent notification with an E-STOP action. */
    fun showSafetyNotification(context: Context, server: String = "") {
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context,
            REQUEST_CODE_SAFETY_OPEN,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        // Intent for E-STOP action
        val estopIntent = GlobalStopRequest.stamp(context, Intent(context, MainActivity::class.java)).apply {
            // Reaches the running activity (launchMode singleTop) instead of stacking a second one.
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val estopPendingIntent = PendingIntent.getActivity(
            context,
            REQUEST_CODE_SAFETY_ESTOP,
            estopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val builder = NotificationCompat.Builder(context, ESTOP_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(context.getString(R.string.notification_safety_title))
            .setContentText(if (server.isBlank()) context.getString(R.string.notification_safety_text) else context.getString(R.string.notification_safety_server_text, server))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true) // Persistent
            .setContentIntent(pendingIntent)
            .addAction(R.drawable.ic_launcher_foreground, context.getString(R.string.stop_all_action), estopPendingIntent)
            .setStyle(androidx.media.app.NotificationCompat.MediaStyle().setShowActionsInCompactView(0))

        val notificationManager: NotificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(PERSISTENT_ID, builder.build())
    }

    /** Removes the persistent safety notification. */
    fun hideSafetyNotification(context: Context) {
        val notificationManager: NotificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(PERSISTENT_ID)
    }
}
