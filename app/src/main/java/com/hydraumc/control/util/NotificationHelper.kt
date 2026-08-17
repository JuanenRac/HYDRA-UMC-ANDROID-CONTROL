// =============================================================================
// HYDRA-UMC CONTROL - Helper for industrial notifications and alerts
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0 - see LICENSE
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
 * Manages system notifications for robotic events.
 */
object NotificationHelper {
    private const val CHANNEL_ID = "hydra_mission_alerts"
    private const val ESTOP_CHANNEL_ID = "hydra_safety_alerts"
    private const val PERSISTENT_ID = 1001

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
        }
    }

    /** Sends a high-priority alert notification. */
    fun sendAlert(context: Context, title: String, message: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)

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

    /** Shows a persistent notification with an E-STOP action. */
    fun showSafetyNotification(context: Context) {
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        
        // Intent for E-STOP action
        val estopIntent = Intent(context, MainActivity::class.java).apply {
            action = "ACTION_GLOBAL_ESTOP"
        }
        val estopPendingIntent = PendingIntent.getActivity(context, 1, estopIntent, PendingIntent.FLAG_IMMUTABLE)

        val builder = NotificationCompat.Builder(context, ESTOP_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("HYDRA-UMC SAFETY")
            .setContentText("Active connection. Quick E-STOP available.")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true) // Persistent
            .setContentIntent(pendingIntent)
            .addAction(R.drawable.ic_launcher_foreground, "STOP ALL ROBOTS", estopPendingIntent)
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
