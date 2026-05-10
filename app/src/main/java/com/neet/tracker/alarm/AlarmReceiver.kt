package com.neet.tracker.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AlarmReceiver : BroadcastReceiver() {

    companion object {
        const val EXTRA_TITLE = "alarm_title"
        const val EXTRA_MESSAGE = "alarm_message"
        const val EXTRA_ID = "alarm_id"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "NEET Reminder"
        val message = intent.getStringExtra(EXTRA_MESSAGE) ?: "Time to study!"
        val id = intent.getIntExtra(EXTRA_ID, System.currentTimeMillis().toInt())

        NotificationHelper.createNotificationChannel(context)
        NotificationHelper.showReminderNotification(context, id, title, message)
    }
}
