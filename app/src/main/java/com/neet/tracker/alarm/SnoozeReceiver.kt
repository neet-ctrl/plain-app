package com.neet.tracker.alarm

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class SnoozeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id        = intent.getIntExtra(AlarmReceiver.EXTRA_ID, 0)
        val title     = intent.getStringExtra(AlarmReceiver.EXTRA_TITLE)      ?: "NEET Reminder"
        val message   = intent.getStringExtra(AlarmReceiver.EXTRA_MESSAGE)    ?: "Time to study!"
        val eventId   = intent.getStringExtra(AlarmReceiver.EXTRA_EVENT_ID)   ?: ""
        val eventType = intent.getStringExtra(AlarmReceiver.EXTRA_EVENT_TYPE) ?: ""

        // Cancel current notification immediately
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(id)

        // Reschedule the same alarm 10 minutes from now
        val snoozeAt = System.currentTimeMillis() + 10L * 60 * 1000
        AlarmScheduler.scheduleAlarm(context, id, snoozeAt, title, message, eventId, eventType)
    }
}
