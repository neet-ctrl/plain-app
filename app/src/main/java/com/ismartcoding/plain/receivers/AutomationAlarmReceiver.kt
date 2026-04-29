package com.ismartcoding.plain.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ismartcoding.plain.helpers.AutomationActionRunner
import com.ismartcoding.plain.helpers.AutomationHelper
import com.ismartcoding.plain.helpers.AutomationScheduler

/**
 * Fired by AlarmManager when a `time` or `scheduled_once` rule should run.
 * After firing, daily rules re-arm themselves to tomorrow same time, and
 * one-shot rules disable themselves so they don't repeat.
 */
class AutomationAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val ruleId = AutomationScheduler.extractRuleId(intent) ?: return
        val rule = AutomationHelper.byId(ruleId, context) ?: return
        AutomationActionRunner.trigger(
            ruleId = ruleId, source = "scheduled",
            context = mapOf("triggered_at" to System.currentTimeMillis().toString()),
            ctx = context,
        )
        when (rule.trigger.type) {
            "time" -> AutomationScheduler.rearmDaily(ruleId, context)
            "scheduled_once" -> AutomationScheduler.completeOnce(ruleId, context)
        }
    }
}
