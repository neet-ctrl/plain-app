package com.ismartcoding.plain.workers

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat
import com.ismartcoding.lib.helpers.CoroutinesHelper.coIO
import com.ismartcoding.lib.logcat.LogCat
import com.ismartcoding.plain.preferences.TelegramBotEnabledPreference
import com.ismartcoding.plain.preferences.TelegramBotForwardCallsPreference
import com.ismartcoding.plain.preferences.TelegramBotForwardNotificationsPreference
import com.ismartcoding.plain.preferences.TelegramBotTokenPreference
import com.ismartcoding.plain.preferences.TelegramChatIdPreference
import com.ismartcoding.plain.preferences.WebPreference
import com.ismartcoding.plain.services.HttpServerService
import com.ismartcoding.plain.telegram.TelegramBotManager

/**
 * JobScheduler-backed fallback that complements KeepAliveWatchdogReceiver.
 * Runs at the OS-enforced minimum (~15 minutes when periodic) but also
 * survives reboot and OEM force-stop scenarios where AlarmManager loses
 * its pending intents. Together they give us "auto-restart within ~1 min
 * during normal use, ~15 min worst case in deep Doze".
 */
class KeepAliveJobService : JobService() {

    override fun onStartJob(params: JobParameters?): Boolean {
        coIO {
            try {
                val ctx = applicationContext
                val webEnabled = WebPreference.getAsync(ctx)
                if (webEnabled && !HttpServerService.isRunning()) {
                    LogCat.d("KeepAliveJobService: HttpServerService dead — restarting")
                    ContextCompat.startForegroundService(
                        ctx,
                        Intent(ctx, HttpServerService::class.java),
                    )
                }
                // Restart Telegram bot if the service is running but the bot is dead.
                try {
                    val botEnabled = TelegramBotEnabledPreference.getAsync(ctx)
                    if (botEnabled && HttpServerService.isRunning() && !TelegramBotManager.isRunning) {
                        val botToken = TelegramBotTokenPreference.getAsync(ctx)
                        val chatId = TelegramChatIdPreference.getAsync(ctx)
                        if (botToken.isNotBlank() && chatId.isNotBlank()) {
                            LogCat.d("KeepAliveJobService: TelegramBot dead — restarting")
                            TelegramBotManager.forwardNotifications = TelegramBotForwardNotificationsPreference.getAsync(ctx)
                            TelegramBotManager.forwardCalls = TelegramBotForwardCallsPreference.getAsync(ctx)
                            TelegramBotManager.start(botToken, chatId)
                        }
                    }
                } catch (t: Throwable) {
                    LogCat.w("KeepAliveJobService: Telegram bot restart failed: ${t.message}")
                }
            } catch (t: Throwable) {
                LogCat.w("KeepAliveJobService failure: ${t.message}")
            } finally {
                jobFinished(params, false)
            }
        }
        return true
    }

    override fun onStopJob(params: JobParameters?): Boolean = true

    companion object {
        private const val JOB_ID = 0xA11FE

        fun schedule(context: Context) {
            val scheduler =
                context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler ?: return
            val component = ComponentName(context, KeepAliveJobService::class.java)
            val builder =
                JobInfo.Builder(JOB_ID, component)
                    .setPersisted(true)
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_NONE)
                    .setPeriodic(15 * 60 * 1000L)
            if (Build.VERSION.SDK_INT >= 26) {
                builder.setRequiresBatteryNotLow(false)
            }
            try {
                scheduler.schedule(builder.build())
                LogCat.d("KeepAliveJobService scheduled")
            } catch (t: Throwable) {
                LogCat.e("KeepAliveJobService schedule failed: ${t.message}")
            }
        }

        fun cancel(context: Context) {
            val scheduler =
                context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler ?: return
            scheduler.cancel(JOB_ID)
        }
    }
}
