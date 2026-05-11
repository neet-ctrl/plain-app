package com.neet.tracker.alarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import androidx.core.app.NotificationCompat
import com.neet.tracker.MainActivity

object NotificationHelper {

    const val CHANNEL_ID       = "neet_reminders_channel"
    const val ALARM_CHANNEL_ID = "neet_alarm_channel"
    const val CHANNEL_NAME     = "NEET Reminders"
    const val ALARM_CHANNEL_NAME = "NEET Alarms"
    const val CHANNEL_DESC     = "Study reminders and alarms for NEET preparation"

    fun createNotificationChannel(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Soft reminders channel (kept for backward compat)
        val softChannel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH).apply {
            description = CHANNEL_DESC
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 300, 200, 300)
            enableLights(true)
        }
        manager.createNotificationChannel(softChannel)

        // Dedicated alarm channel with alarm ringtone
        val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val audioAttrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val alarmChannel = NotificationChannel(ALARM_CHANNEL_ID, ALARM_CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH).apply {
            description = CHANNEL_DESC
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 500, 300, 500, 300, 500)
            enableLights(true)
            setSound(alarmUri, audioAttrs)
        }
        manager.createNotificationChannel(alarmChannel)
    }

    fun showReminderNotification(
        context: Context,
        notificationId: Int,
        title: String,
        message: String,
        eventId: String = "",
        eventType: String = ""
    ) {
        val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        // ── Open Card / tap intent ─────────────────────────────────────────────
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_EVENT_ID,   eventId)
            putExtra(MainActivity.EXTRA_EVENT_TYPE, eventType)
        }
        val openPi = PendingIntent.getActivity(
            context, notificationId + 30000, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Full-screen heads-up intent (same destination)
        val fullScreenPi = PendingIntent.getActivity(
            context, notificationId + 40000, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // ── Snooze action ──────────────────────────────────────────────────────
        val snoozeIntent = Intent(context, SnoozeReceiver::class.java).apply {
            putExtra(AlarmReceiver.EXTRA_ID,         notificationId)
            putExtra(AlarmReceiver.EXTRA_TITLE,      title)
            putExtra(AlarmReceiver.EXTRA_MESSAGE,    message)
            putExtra(AlarmReceiver.EXTRA_EVENT_ID,   eventId)
            putExtra(AlarmReceiver.EXTRA_EVENT_TYPE, eventType)
        }
        val snoozePi = PendingIntent.getBroadcast(
            context, notificationId + 10000, snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // ── Dismiss action ─────────────────────────────────────────────────────
        val dismissIntent = Intent(context, DismissReceiver::class.java).apply {
            putExtra(AlarmReceiver.EXTRA_ID, notificationId)
        }
        val dismissPi = PendingIntent.getBroadcast(
            context, notificationId + 20000, dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, ALARM_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(openPi)
            .setFullScreenIntent(fullScreenPi, true)
            .setVibrate(longArrayOf(0, 500, 300, 500, 300, 500))
            .setSound(alarmUri)
            .setAutoCancel(false)
            .setOngoing(false)
            .addAction(android.R.drawable.ic_media_pause, "Snooze 10 min", snoozePi)
            .addAction(android.R.drawable.ic_delete,      "Dismiss",        dismissPi)
            .addAction(android.R.drawable.ic_menu_today,  "Open Card",      openPi)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(notificationId, notification)
    }
}
