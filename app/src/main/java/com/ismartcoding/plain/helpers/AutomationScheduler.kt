package com.ismartcoding.plain.helpers

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.ismartcoding.lib.logcat.LogCat
import com.ismartcoding.plain.MainApp
import com.ismartcoding.plain.receivers.AutomationAlarmReceiver
import java.util.Calendar

/**
 * Wires up time-based triggers to the OS AlarmManager.
 *
 *  • `time` triggers (HH:MM + days mask) become repeating daily alarms that
 *    re-arm themselves the next day when fired.
 *  • `scheduled_once` triggers fire once at an absolute timestamp and the
 *    rule auto-disables itself afterwards.
 *
 * Re-call [scheduleAll] after rule add/edit/delete and on boot.
 */
object AutomationScheduler {

    private const val ACTION = "com.ismartcoding.plain.AUTOMATION_FIRE"
    private const val EXTRA_RULE_ID = "ruleId"

    fun scheduleAll(ctx: Context = MainApp.instance) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        for (r in AutomationHelper.list(ctx)) {
            cancel(r.id, ctx)
            if (!r.enabled) continue
            when (r.trigger.type) {
                "time" -> scheduleDaily(am, r, ctx)
                "scheduled_once" -> scheduleOnce(am, r, ctx)
            }
        }
    }

    fun scheduleRule(ruleId: String, ctx: Context = MainApp.instance) {
        val r = AutomationHelper.byId(ruleId, ctx) ?: return
        cancel(r.id, ctx)
        if (!r.enabled) return
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        when (r.trigger.type) {
            "time" -> scheduleDaily(am, r, ctx)
            "scheduled_once" -> scheduleOnce(am, r, ctx)
        }
    }

    fun cancel(ruleId: String, ctx: Context = MainApp.instance) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pendingFor(ruleId, ctx))
    }

    private fun scheduleDaily(am: AlarmManager, r: AutomationHelper.Rule, ctx: Context) {
        val h = r.trigger.params["hour"]?.toIntOrNull() ?: return
        val m = r.trigger.params["minute"]?.toIntOrNull() ?: 0
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, h); set(Calendar.MINUTE, m)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }
        setExactCompat(am, cal.timeInMillis, pendingFor(r.id, ctx))
    }

    private fun scheduleOnce(am: AlarmManager, r: AutomationHelper.Rule, ctx: Context) {
        val at = r.trigger.params["atMs"]?.toLongOrNull() ?: return
        if (at <= System.currentTimeMillis()) return
        setExactCompat(am, at, pendingFor(r.id, ctx))
    }

    private fun setExactCompat(am: AlarmManager, atMs: Long, pi: PendingIntent) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMs, pi)
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMs, pi)
            }
        } catch (t: Throwable) {
            LogCat.e("AutomationScheduler.setExact: ${t.message}")
            try { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMs, pi) } catch (_: Throwable) {}
        }
    }

    private fun pendingFor(ruleId: String, ctx: Context): PendingIntent {
        val i = Intent(ctx, AutomationAlarmReceiver::class.java).apply {
            action = ACTION; putExtra(EXTRA_RULE_ID, ruleId)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(ctx, ruleId.hashCode(), i, flags)
    }

    fun extractRuleId(i: Intent?): String? = i?.getStringExtra(EXTRA_RULE_ID)

    /** After firing a daily alarm, push it forward to tomorrow same time. */
    fun rearmDaily(ruleId: String, ctx: Context = MainApp.instance) {
        val r = AutomationHelper.byId(ruleId, ctx) ?: return
        if (!r.enabled || r.trigger.type != "time") return
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        scheduleDaily(am, r, ctx)
    }

    /** Disables a one-shot scheduled rule after it fires. */
    fun completeOnce(ruleId: String, ctx: Context = MainApp.instance) {
        AutomationHelper.setEnabled(ruleId, false, ctx)
        cancel(ruleId, ctx)
    }
}
