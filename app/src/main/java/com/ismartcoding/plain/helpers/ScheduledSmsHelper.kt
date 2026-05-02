package com.ismartcoding.plain.helpers

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.ismartcoding.lib.logcat.LogCat
import com.ismartcoding.plain.MainApp
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

@Serializable
data class ScheduledSmsItem(
    val id: String,
    val recipient: String,
    val message: String,
    val sendAt: Long,
    val sent: Boolean = false,
    val overdue: Boolean = false,
)

object ScheduledSmsHelper {

    private const val PREFS_NAME = "scheduled_sms_prefs"
    private const val KEY_LIST = "scheduled_sms_list"
    private const val ACTION_SEND = "com.ismartcoding.plain.SEND_SCHEDULED_SMS"
    private const val EXTRA_ID = "sms_id"

    private fun prefs() = MainApp.instance.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getAll(): List<ScheduledSmsItem> {
        val json = prefs().getString(KEY_LIST, "[]") ?: "[]"
        return try {
            val items = Json.decodeFromString<List<ScheduledSmsItem>>(json)
            val now = System.currentTimeMillis()
            items.map { it.copy(overdue = !it.sent && it.sendAt < now) }
        } catch (_: Exception) { emptyList() }
    }

    fun add(recipient: String, message: String, sendAt: Long): ScheduledSmsItem {
        val id = UUID.randomUUID().toString()
        val item = ScheduledSmsItem(id = id, recipient = recipient, message = message, sendAt = sendAt)
        val current = getAll().toMutableList()
        current.add(item)
        save(current)
        scheduleAlarm(item)
        return item
    }

    fun delete(id: String): Boolean {
        val current = getAll().toMutableList()
        val removed = current.removeIf { it.id == id }
        if (removed) {
            save(current)
            cancelAlarm(id)
        }
        return removed
    }

    fun markSent(id: String) {
        val current = getAll().toMutableList()
        val idx = current.indexOfFirst { it.id == id }
        if (idx >= 0) {
            current[idx] = current[idx].copy(sent = true)
            save(current)
        }
    }

    private fun save(items: List<ScheduledSmsItem>) {
        prefs().edit().putString(KEY_LIST, Json.encodeToString(items)).apply()
    }

    private fun pendingFlags() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    else PendingIntent.FLAG_UPDATE_CURRENT

    private fun makePendingIntent(id: String): PendingIntent {
        val ctx = MainApp.instance
        val intent = Intent(ACTION_SEND).apply {
            setPackage(ctx.packageName)
            putExtra(EXTRA_ID, id)
        }
        return PendingIntent.getBroadcast(ctx, id.hashCode(), intent, pendingFlags())
    }

    private fun scheduleAlarm(item: ScheduledSmsItem) {
        val am = MainApp.instance.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = makePendingIntent(item.id)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, item.sendAt, pi)
            } else {
                am.setExact(AlarmManager.RTC_WAKEUP, item.sendAt, pi)
            }
        } catch (e: Exception) {
            LogCat.e("ScheduledSmsHelper scheduleAlarm: ${e.message}")
        }
    }

    private fun cancelAlarm(id: String) {
        val am = MainApp.instance.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(makePendingIntent(id))
    }
}

class ScheduledSmsSenderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "com.ismartcoding.plain.SEND_SCHEDULED_SMS") return
        val id = intent.getStringExtra("sms_id") ?: return
        val item = ScheduledSmsHelper.getAll().find { it.id == id && !it.sent } ?: return
        try {
            com.ismartcoding.plain.features.sms.SmsHelper.sendText(item.recipient, item.message)
            ScheduledSmsHelper.markSent(id)
            LogCat.d("ScheduledSms: sent to ${item.recipient}")
        } catch (e: Exception) {
            LogCat.e("ScheduledSms send failed: ${e.message}")
        }
    }
}
