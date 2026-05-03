package com.ismartcoding.plain.services

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.ServiceInfo
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleService
import com.ismartcoding.lib.helpers.CoroutinesHelper.coIO
import com.ismartcoding.lib.logcat.LogCat
import com.ismartcoding.plain.Constants
import com.ismartcoding.plain.R
import com.ismartcoding.plain.api.HttpClientManager
import com.ismartcoding.plain.enums.HttpServerState
import com.ismartcoding.plain.helpers.NotificationHelper
import com.ismartcoding.plain.helpers.UrlHelper
import com.ismartcoding.plain.web.HttpServerManager
import com.ismartcoding.plain.mdns.MdnsRegister
import com.ismartcoding.plain.mdns.NsdHelper
import com.ismartcoding.plain.TempData
import com.ismartcoding.plain.preferences.KeepAliveWatchdogEnabledPreference
import com.ismartcoding.plain.preferences.TelegramBotEnabledPreference
import com.ismartcoding.plain.preferences.CloudflareTunnelEnabledPreference
import com.ismartcoding.plain.preferences.CloudflareTunnelTokenPreference
import com.ismartcoding.plain.preferences.TelegramBotPasswordPreference
import com.ismartcoding.plain.preferences.TelegramBotPasswordEnabledPreference
import com.ismartcoding.plain.preferences.TelegramBotForwardCallsPreference
import com.ismartcoding.plain.preferences.TelegramBotForwardNotificationsPreference
import com.ismartcoding.plain.preferences.TelegramBotForwardSmsPreference
import com.ismartcoding.plain.preferences.TelegramBotForwardGeofencePreference
import com.ismartcoding.plain.preferences.TelegramBotForwardBatteryAlertPreference
import com.ismartcoding.plain.preferences.TelegramBotBatteryAlertThresholdPreference
import com.ismartcoding.plain.preferences.TelegramBotForwardStealthShotsPreference
import com.ismartcoding.plain.preferences.TelegramBotTokenPreference
import com.ismartcoding.plain.preferences.TelegramChatIdPreference
import com.ismartcoding.plain.receivers.KeepAliveWatchdogReceiver
import com.ismartcoding.plain.telegram.TelegramBotManager
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking

class HttpServerService : LifecycleService() {
    private var serverState: HttpServerState = HttpServerState.OFF
    private var mdnsRegister: MdnsRegister? = null
    private var serverJob: Job? = null
    private var lockManager: HttpServerLockManager? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        NotificationHelper.ensureDefaultChannel()

        lockManager = HttpServerLockManager(this)
        mdnsRegister = MdnsRegister(
            context = this,
            isActive = { serverState == HttpServerState.ON },
            hostnameProvider = { TempData.mdnsHostname },
            httpPortProvider = { TempData.httpPort },
            httpsPortProvider = { TempData.httpsPort },
        ).also { it.start() }
        
        lifecycle.addObserver(object : LifecycleEventObserver {
            override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
                when (event) {
                    Lifecycle.Event.ON_START -> {
                        lockManager?.start()
                        serverJob?.cancel()
                        serverJob = coIO {
                            startHttpServerAsync()
                        }
                    }

                    Lifecycle.Event.ON_STOP -> {
                        lockManager?.stop()
                        serverJob?.cancel()
                        serverJob = coIO {
                            stopHttpServerAsync()
                        }
                    }

                    else -> Unit
                }
            }
        })
    }
    
    @SuppressLint("InlinedApi")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        
        try {
            val notification = NotificationHelper.createServiceNotification(
                this,
                Constants.ACTION_STOP_HTTP_SERVER,
                getString(R.string.api_service_is_running),
                HttpServerManager.getNotificationContent()
            )
            
            try {
                ServiceCompat.startForeground(
                    this, 
                    HttpServerManager.notificationId,
                    notification, 
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } catch (e: Exception) {
                LogCat.e("Error starting foreground service with specialUse: ${e.message}")
                try {
                    ServiceCompat.startForeground(
                        this, 
                        HttpServerManager.notificationId,
                        notification, 
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                    )
                } catch (e2: Exception) {
                    LogCat.e("Error starting foreground service with dataSync: ${e2.message}")
                    startForeground(HttpServerManager.notificationId, notification)
                }
            }
        } catch (e: Exception) {
            LogCat.e("Failed to start foreground service: ${e.message}")
            e.printStackTrace()
            stopSelf()
            return START_NOT_STICKY
        }

        // Arm the keep-alive watchdog so the web server is re-launched if the OS kills it.
        try {
            val watchdog = runBlocking { KeepAliveWatchdogEnabledPreference.getAsync(applicationContext) }
            if (watchdog) KeepAliveWatchdogReceiver.schedule(applicationContext)
        } catch (_: Throwable) {}

        return START_STICKY
    }

    private suspend fun startHttpServerAsync() {
        HttpServerStartHelper.startServer(this) { serverState = it }
        startTelegramBotAsync()
        startCloudflareTunnelAsync()
    }

    private suspend fun startCloudflareTunnelAsync() {
        try {
            val enabled = CloudflareTunnelEnabledPreference.getAsync(applicationContext)
            if (!enabled) return
            val cfToken = CloudflareTunnelTokenPreference.getAsync(applicationContext)
            if (cfToken.isNotBlank() && !CloudflareTunnelService.isRunning()) {
                CloudflareTunnelManager.start(applicationContext)
            }
        } catch (e: Exception) {
            LogCat.e("CloudflareTunnel auto-start failed: ${e.message}")
        }
    }

    private suspend fun startTelegramBotAsync() {
        try {
            val enabled = TelegramBotEnabledPreference.getAsync(applicationContext)
            if (!enabled) return
            val botToken = TelegramBotTokenPreference.getAsync(applicationContext)
            val chatId = TelegramChatIdPreference.getAsync(applicationContext)
            if (botToken.isNotBlank() && chatId.isNotBlank()) {
                TelegramBotManager.forwardNotifications = TelegramBotForwardNotificationsPreference.getAsync(applicationContext)
                TelegramBotManager.forwardCalls = TelegramBotForwardCallsPreference.getAsync(applicationContext)
                TelegramBotManager.forwardSmsEnabled = TelegramBotForwardSmsPreference.getAsync(applicationContext)
                TelegramBotManager.forwardGeofenceEnabled = TelegramBotForwardGeofencePreference.getAsync(applicationContext)
                TelegramBotManager.forwardBatteryAlertEnabled = TelegramBotForwardBatteryAlertPreference.getAsync(applicationContext)
                TelegramBotManager.batteryAlertThreshold = TelegramBotBatteryAlertThresholdPreference.getAsync(applicationContext)
                TelegramBotManager.forwardStealthShotsEnabled = TelegramBotForwardStealthShotsPreference.getAsync(applicationContext)
                TelegramBotManager.botPasswordEnabled = TelegramBotPasswordEnabledPreference.getAsync(applicationContext)
                TelegramBotManager.botPassword = TelegramBotPasswordPreference.getAsync(applicationContext)
                TelegramBotManager.start(botToken, chatId)
            }
        } catch (e: Exception) {
            LogCat.e("TelegramBot start failed: ${e.message}")
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // User swiped the app from recents. We do NOT stop the web server — it must
        // keep running like the Cloudflare tunnel does. Re-arm the foreground service
        // so Android keeps it alive after the task is gone.
        LogCat.d("HttpServerService.onTaskRemoved — re-arming foreground service")
        try {
            val restart = Intent(applicationContext, HttpServerService::class.java)
            ContextCompat.startForegroundService(applicationContext, restart)
        } catch (t: Throwable) {
            LogCat.e("HttpServerService self re-start in onTaskRemoved failed: ${t.message}")
        }
        try {
            val watchdog = runBlocking { KeepAliveWatchdogEnabledPreference.getAsync(applicationContext) }
            if (watchdog) KeepAliveWatchdogReceiver.schedule(applicationContext)
        } catch (_: Throwable) {}
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        instance = null
        serverJob?.cancel()
        serverJob = null
        try { TelegramBotManager.stop() } catch (_: Throwable) {}
        super.onDestroy()
        lockManager?.stop()
        lockManager = null
        mdnsRegister?.stop()
        mdnsRegister = null
        // Ensure mDNS responder is stopped
        NsdHelper.unregisterService()
        HttpServerManager.server = null
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private suspend fun stopHttpServerAsync() {
        LogCat.d("stopHttpServer")
        try {
            // Stop mDNS responder
            NsdHelper.unregisterService()
            
            val client = HttpClientManager.httpClient()
            val r = client.get(UrlHelper.getShutdownUrl())
            if (r.status == HttpStatusCode.Gone) {
                LogCat.d("http server is stopped")
            }
        } catch (ex: Exception) {
            LogCat.e("Graceful shutdown failed: ${ex.message}")
            // Fallback: force stop via stored server reference
            try {
                HttpServerManager.server?.stop(500, 1000)
                LogCat.d("Server force-stopped via stored reference")
            } catch (e: Exception) {
                LogCat.e("Force stop also failed: ${e.message}")
            }
        }
        HttpServerManager.server = null
        PNotificationListenerService.toggle(this, false)

        serverState = HttpServerState.OFF
    }

    companion object {
        @Volatile
        var instance: HttpServerService? = null

        fun isRunning(): Boolean = instance != null
    }
}
