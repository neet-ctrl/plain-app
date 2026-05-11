package com.neet.tracker.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AlarmReceiver : BroadcastReceiver() {

    companion object {
        const val EXTRA_TITLE      = "alarm_title"
        const val EXTRA_MESSAGE    = "alarm_message"
        const val EXTRA_ID         = "alarm_id"
        const val EXTRA_EVENT_ID   = "alarm_event_id"
        const val EXTRA_EVENT_TYPE = "alarm_event_type"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val title     = intent.getStringExtra(EXTRA_TITLE)      ?: "NEET Reminder"
        val message   = intent.getStringExtra(EXTRA_MESSAGE)    ?: "Time to study!"
        val id        = intent.getIntExtra(EXTRA_ID, System.currentTimeMillis().toInt())
        val eventId   = intent.getStringExtra(EXTRA_EVENT_ID)   ?: ""
        val eventType = intent.getStringExtra(EXTRA_EVENT_TYPE) ?: ""

        NotificationHelper.createNotificationChannel(context)
        NotificationHelper.showReminderNotification(context, id, title, message, eventId, eventType)
    }
}
