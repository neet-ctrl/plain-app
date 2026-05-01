package com.ismartcoding.plain.services

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.IBinder
import com.ismartcoding.lib.logcat.LogCat
import com.ismartcoding.plain.MainApp
import com.ismartcoding.plain.helpers.BatteryHistoryHelper
import com.ismartcoding.plain.telegram.TelegramBotManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Lightweight always-running battery sampler. Listens for ACTION_BATTERY_CHANGED
 * broadcasts (which the OS already throws on every level change) and writes a
 * sample whenever charger state flips or the periodic timer fires.
 *
 * No notification — this is a normal (non-foreground) service kept alive by the
 * existing HttpServer foreground service. If the app is killed, sampling pauses
 * until the next launch.
 */
class BatterySamplerService : Service() {

    private val scope = CoroutineScope(Dispatchers.IO)
    private var loop: Job? = null
    private var lastBatteryLevel: Int = -1
    private var lastChargingState: Boolean? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != Intent.ACTION_BATTERY_CHANGED) return
            try {
                val s = BatteryHistoryHelper.sampleNow(context)
                if (s != null) {
                    if (BatteryHistoryHelper.isEnabled()) {
                        BatteryHistoryHelper.appendSample(s, context)
                    }
                    fireAutomationOnBatteryChange(context, s)
                    if (s.plugged == 0) {
                        TelegramBotManager.forwardBatteryAlert(s.level)
                    }
                }
            } catch (t: Throwable) {
                LogCat.e("BatterySampler: ${t.message}")
            }
        }
    }

    /**
     * Fan out battery-level / charging-state changes into the automation engine
     * so user-defined rules like "battery < 20% → send SMS" or
     * "started charging → speak hello" actually fire.
     */
    private fun fireAutomationOnBatteryChange(
        context: Context,
        sample: com.ismartcoding.plain.helpers.BatteryHistoryHelper.Sample,
    ) {
        if (!com.ismartcoding.plain.helpers.AutomationHelper.isEnabled(context)) return
        val isCharging = sample.plugged != 0
        val rules = com.ismartcoding.plain.helpers.AutomationHelper.list(context)
        for (r in rules) {
            if (!r.enabled) continue
            when (r.trigger.type) {
                "battery_level" -> {
                    val th = r.trigger.params["threshold"]?.toIntOrNull() ?: continue
                    val op = r.trigger.params["op"] ?: "<"
                    val crossed = when (op) {
                        "<" -> sample.level < th && (lastBatteryLevel < 0 || lastBatteryLevel >= th)
                        "<=" -> sample.level <= th && (lastBatteryLevel < 0 || lastBatteryLevel > th)
                        ">" -> sample.level > th && (lastBatteryLevel < 0 || lastBatteryLevel <= th)
                        ">=" -> sample.level >= th && (lastBatteryLevel < 0 || lastBatteryLevel < th)
                        "==" -> sample.level == th && lastBatteryLevel != th
                        else -> false
                    }
                    if (crossed) {
                        com.ismartcoding.plain.helpers.AutomationActionRunner.trigger(
                            ruleId = r.id, source = "trigger",
                            context = mapOf(
                                "battery_level" to sample.level.toString(),
                                "charging" to isCharging.toString(),
                            ),
                            ctx = context,
                        )
                    }
                }
                "battery_charging" -> {
                    val want = r.trigger.params["state"] == "true"
                    if (lastChargingState != null && lastChargingState != isCharging && isCharging == want) {
                        com.ismartcoding.plain.helpers.AutomationActionRunner.trigger(
                            ruleId = r.id, source = "trigger",
                            context = mapOf(
                                "battery_level" to sample.level.toString(),
                                "charging" to isCharging.toString(),
                            ),
                            ctx = context,
                        )
                    }
                }
            }
        }
        lastBatteryLevel = sample.level
        lastChargingState = isCharging
    }

    override fun onCreate() {
        super.onCreate()
        try {
            registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        } catch (t: Throwable) {
            LogCat.e("BatterySampler register: ${t.message}")
        }
        loop = scope.launch {
            while (isActive) {
                if (BatteryHistoryHelper.isEnabled()) {
                    try {
                        val s = BatteryHistoryHelper.sampleNow(MainApp.instance)
                        if (s != null) BatteryHistoryHelper.appendSample(s, MainApp.instance)
                    } catch (_: Throwable) { /* ignore */ }
                }
                delay(BatteryHistoryHelper.SAMPLE_INTERVAL_MS)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        try { unregisterReceiver(receiver) } catch (_: Throwable) {}
        loop?.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        fun start(ctx: Context = MainApp.instance) {
            try {
                ctx.startService(Intent(ctx, BatterySamplerService::class.java))
            } catch (t: Throwable) {
                LogCat.e("BatterySampler start: ${t.message}")
            }
        }
    }
}
