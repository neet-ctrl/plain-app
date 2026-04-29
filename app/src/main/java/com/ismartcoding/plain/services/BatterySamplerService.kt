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
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != Intent.ACTION_BATTERY_CHANGED) return
            if (!BatteryHistoryHelper.isEnabled()) return
            try {
                val s = BatteryHistoryHelper.sampleNow(context) ?: return
                BatteryHistoryHelper.appendSample(s, context)
            } catch (t: Throwable) {
                LogCat.e("BatterySampler: ${t.message}")
            }
        }
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
