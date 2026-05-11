package com.neet.tracker.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != "android.intent.action.QUICKBOOT_POWERON") return

        val prefs: SharedPreferences = context.getSharedPreferences("neet_alarms", Context.MODE_PRIVATE)
        val json = prefs.getString("saved_alarms", null) ?: return
        val type = object : TypeToken<List<SavedAlarm>>() {}.type
        val alarms: List<SavedAlarm> = Gson().fromJson(json, type) ?: return

        val now = System.currentTimeMillis()
        alarms.forEach { alarm ->
            if (alarm.triggerAtMillis > now) {
                AlarmScheduler.scheduleAlarm(
                    context,
                    alarm.id,
                    alarm.triggerAtMillis,
                    alarm.title,
                    alarm.message,
                    alarm.eventId,
                    alarm.eventType
                )
            }
        }
    }
}

data class SavedAlarm(
    val id: Int = 0,
    val triggerAtMillis: Long = 0L,
    val title: String = "",
    val message: String = "",
    val eventId: String = "",
    val eventType: String = ""
)
