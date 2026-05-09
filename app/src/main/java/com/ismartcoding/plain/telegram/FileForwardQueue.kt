package com.ismartcoding.plain.telegram

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.Locale

class FileForwardQueue private constructor(ctx: Context) {

    companion object {
        private const val DB_NAME = "file_forward.db"
        private const val DB_VERSION = 1
        private const val TABLE = "uploads"

        const val STATUS_PENDING = 0
        const val STATUS_UPLOADING = 1
        const val STATUS_DONE = 2
        const val STATUS_FAILED = 3
        const val MAX_RETRIES = 3
        const val MAX_UPLOAD_BYTES = 49L * 1024 * 1024

        private const val TRACKER_PREFS = "file_fwd_tracker_prefs"
        private const val KEY_LAST_ALIVE = "last_alive_ms"
        private const val KEY_RECOVERY_SINCE = "rec_offline_since"
        private const val KEY_RECOVERY_GAP = "rec_gap_ms"
        private const val KEY_RECOVERY_FOUND = "rec_files_found"
        private const val KEY_RECOVERY_READY = "rec_ready"

        @Volatile private var _instance: FileForwardQueue? = null

        fun get(ctx: Context): FileForwardQueue {
            return _instance ?: synchronized(this) {
                _instance ?: FileForwardQueue(ctx.applicationContext).also { _instance = it }
            }
        }

        fun recordAliveTime(ctx: Context) {
            ctx.getSharedPreferences(TRACKER_PREFS, Context.MODE_PRIVATE)
                .edit().putLong(KEY_LAST_ALIVE, System.currentTimeMillis()).apply()
        }

        fun getLastAliveTime(ctx: Context): Long {
            return ctx.getSharedPreferences(TRACKER_PREFS, Context.MODE_PRIVATE)
                .getLong(KEY_LAST_ALIVE, 0)
        }

        fun recordRecovery(ctx: Context, offlineSince: Long, gapMs: Long, filesFound: Int) {
            ctx.getSharedPreferences(TRACKER_PREFS, Context.MODE_PRIVATE).edit()
                .putLong(KEY_RECOVERY_SINCE, offlineSince)
                .putLong(KEY_RECOVERY_GAP, gapMs)
                .putInt(KEY_RECOVERY_FOUND, filesFound)
                .putBoolean(KEY_RECOVERY_READY, true)
                .apply()
        }

        fun getAndClearRecovery(ctx: Context): RecoveryInfo? {
            val p = ctx.getSharedPreferences(TRACKER_PREFS, Context.MODE_PRIVATE)
            if (!p.getBoolean(KEY_RECOVERY_READY, false)) return null
            val info = RecoveryInfo(
                offlineSince = p.getLong(KEY_RECOVERY_SINCE, 0),
                gapMs = p.getLong(KEY_RECOVERY_GAP, 0),
                filesFound = p.getInt(KEY_RECOVERY_FOUND, 0),
            )
            p.edit().putBoolean(KEY_RECOVERY_READY, false).apply()
            return info
        }

        fun md5(f: File): String? {
            return try {
                val md = MessageDigest.getInstance("MD5")
                FileInputStream(f).use { fis ->
                    val buf = ByteArray(8192)
                    var n: Int
                    while (fis.read(buf).also { n = it } != -1) md.update(buf, 0, n)
                }
                md.digest().joinToString("") { "%02x".format(it) }
            } catch (e: Exception) { null }
        }

        fun guessMime(path: String): String {
            val low = path.lowercase(Locale.ROOT)
            if (low.endsWith(".jpg") || low.endsWith(".jpeg")) return "image/jpeg"
            if (low.endsWith(".png")) return "image/png"
            if (low.endsWith(".gif")) return "image/gif"
            if (low.endsWith(".webp")) return "image/webp"
            if (low.endsWith(".heic") || low.endsWith(".heif")) return "image/heic"
            if (low.endsWith(".bmp")) return "image/bmp"
            if (low.endsWith(".tif") || low.endsWith(".tiff")) return "image/tiff"
            if (low.endsWith(".avif")) return "image/avif"
            if (low.endsWith(".svg")) return "image/svg+xml"
            if (low.endsWith(".mp4") || low.endsWith(".m4v")) return "video/mp4"
            if (low.endsWith(".mkv")) return "video/x-matroska"
            if (low.endsWith(".3gp")) return "video/3gpp"
            if (low.endsWith(".avi")) return "video/x-msvideo"
            if (low.endsWith(".mov")) return "video/quicktime"
            if (low.endsWith(".webm")) return "video/webm"
            if (low.endsWith(".flv")) return "video/x-flv"
            if (low.endsWith(".wmv")) return "video/x-ms-wmv"
            if (low.endsWith(".mpg") || low.endsWith(".mpeg")) return "video/mpeg"
            if (low.endsWith(".ts") && !low.endsWith(".m2ts") && !low.endsWith(".mts")) return "video/mp2t"
            if (low.endsWith(".mp3")) return "audio/mpeg"
            if (low.endsWith(".m4a")) return "audio/mp4"
            if (low.endsWith(".ogg") || low.endsWith(".oga")) return "audio/ogg"
            if (low.endsWith(".wav")) return "audio/wav"
            if (low.endsWith(".aac")) return "audio/aac"
            if (low.endsWith(".opus")) return "audio/opus"
            if (low.endsWith(".flac")) return "audio/flac"
            if (low.endsWith(".wma")) return "audio/x-ms-wma"
            if (low.endsWith(".amr")) return "audio/amr"
            if (low.endsWith(".aiff") || low.endsWith(".aif")) return "audio/aiff"
            if (low.endsWith(".pdf")) return "application/pdf"
            if (low.endsWith(".doc")) return "application/msword"
            if (low.endsWith(".docx")) return "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            if (low.endsWith(".xls")) return "application/vnd.ms-excel"
            if (low.endsWith(".xlsx")) return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            if (low.endsWith(".ppt")) return "application/vnd.ms-powerpoint"
            if (low.endsWith(".pptx")) return "application/vnd.openxmlformats-officedocument.presentationml.presentation"
            if (low.endsWith(".txt")) return "text/plain"
            if (low.endsWith(".rtf")) return "application/rtf"
            if (low.endsWith(".csv")) return "text/csv"
            if (low.endsWith(".epub")) return "application/epub+zip"
            if (low.endsWith(".zip")) return "application/zip"
            if (low.endsWith(".rar")) return "application/x-rar-compressed"
            if (low.endsWith(".7z")) return "application/x-7z-compressed"
            if (low.endsWith(".tar")) return "application/x-tar"
            if (low.endsWith(".gz")) return "application/gzip"
            if (low.endsWith(".apk")) return "application/vnd.android.package-archive"
            if (low.endsWith(".json")) return "application/json"
            if (low.endsWith(".xml")) return "text/xml"
            if (low.endsWith(".html") || low.endsWith(".htm")) return "text/html"
            if (low.endsWith(".db") || low.endsWith(".sqlite") || low.endsWith(".sqlite3")) return "application/vnd.sqlite3"
            if (low.endsWith(".log") || low.endsWith(".txt") || low.endsWith(".md")) return "text/plain"
            return "application/octet-stream"
        }

        fun formatSize(bytes: Long): String = when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
            bytes < 1024L * 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024))
            else -> "%.2f GB".format(bytes / (1024.0 * 1024 * 1024))
        }
    }

    data class RecoveryInfo(val offlineSince: Long, val gapMs: Long, val filesFound: Int)

    data class Entry(
        val id: Long,
        val path: String,
        val hash: String,
        val size: Long,
        val mime: String,
        val detectedAt: Long,
        val status: Int,
        val retries: Int,
        val tag: String,
    )

    private val db: SQLiteDatabase = DbHelper(ctx).writableDatabase

    fun enqueue(path: String, tag: String): Boolean {
        if (path.isEmpty()) return false
        val f = File(path)
        if (!f.exists() || !f.isFile || f.length() == 0L) return false
        val hash = md5(f) ?: return false
        val c = db.rawQuery("SELECT id FROM $TABLE WHERE hash=?", arrayOf(hash))
        val exists = c.moveToFirst()
        c.close()
        if (exists) return false
        return try {
            val cv = ContentValues().apply {
                put("path", path)
                put("hash", hash)
                put("size", f.length())
                put("mime", guessMime(path))
                put("detected_at", System.currentTimeMillis())
                put("status", STATUS_PENDING)
                put("retries", 0)
                put("tag", tag)
            }
            db.insertOrThrow(TABLE, null, cv)
            true
        } catch (e: Exception) { false }
    }

    fun nextPending(): Entry? {
        val c = db.rawQuery(
            "SELECT id,path,hash,size,mime,detected_at,status,retries,tag FROM $TABLE" +
                " WHERE (status=$STATUS_PENDING OR (status=$STATUS_FAILED AND retries<$MAX_RETRIES))" +
                " ORDER BY detected_at ASC LIMIT 1",
            null,
        )
        if (!c.moveToFirst()) { c.close(); return null }
        val e = Entry(
            id = c.getLong(0), path = c.getString(1), hash = c.getString(2),
            size = c.getLong(3), mime = c.getString(4), detectedAt = c.getLong(5),
            status = c.getInt(6), retries = c.getInt(7), tag = c.getString(8),
        )
        c.close()
        db.update(TABLE, ContentValues().apply { put("status", STATUS_UPLOADING) }, "id=?", arrayOf(e.id.toString()))
        return e
    }

    fun markDone(id: Long) {
        db.update(TABLE, ContentValues().apply { put("status", STATUS_DONE) }, "id=?", arrayOf(id.toString()))
    }

    fun markFailed(id: Long) {
        db.execSQL("UPDATE $TABLE SET status=$STATUS_FAILED, retries=retries+1 WHERE id=$id")
    }

    fun resetStuck() {
        db.update(TABLE, ContentValues().apply { put("status", STATUS_PENDING) }, "status=?", arrayOf(STATUS_UPLOADING.toString()))
    }

    fun resetFailed(): Int {
        return db.update(
            TABLE,
            ContentValues().apply { put("status", STATUS_PENDING); put("retries", 0) },
            "status=? AND retries>=?",
            arrayOf(STATUS_FAILED.toString(), MAX_RETRIES.toString()),
        )
    }

    fun countPending(): Long {
        val c = db.rawQuery(
            "SELECT COUNT(*) FROM $TABLE WHERE status=$STATUS_PENDING OR (status=$STATUS_FAILED AND retries<$MAX_RETRIES)",
            null,
        )
        val n = if (c.moveToFirst()) c.getLong(0) else 0L; c.close(); return n
    }

    fun countDone(): Long {
        val c = db.rawQuery("SELECT COUNT(*) FROM $TABLE WHERE status=$STATUS_DONE", null)
        val n = if (c.moveToFirst()) c.getLong(0) else 0L; c.close(); return n
    }

    fun countFailed(): Long {
        val c = db.rawQuery("SELECT COUNT(*) FROM $TABLE WHERE status=$STATUS_FAILED AND retries>=$MAX_RETRIES", null)
        val n = if (c.moveToFirst()) c.getLong(0) else 0L; c.close(); return n
    }

    fun totalDoneBytes(): Long {
        val c = db.rawQuery("SELECT SUM(size) FROM $TABLE WHERE status=$STATUS_DONE", null)
        val n = if (c.moveToFirst()) c.getLong(0) else 0L; c.close(); return n
    }

    fun countTotal(): Long {
        val c = db.rawQuery("SELECT COUNT(*) FROM $TABLE", null)
        val n = if (c.moveToFirst()) c.getLong(0) else 0L; c.close(); return n
    }

    fun recentUploads(limit: Int): List<Entry> {
        val list = mutableListOf<Entry>()
        val c = db.rawQuery(
            "SELECT id,path,hash,size,mime,detected_at,status,retries,tag FROM $TABLE" +
                " WHERE status=$STATUS_DONE ORDER BY detected_at DESC LIMIT $limit",
            null,
        )
        while (c.moveToNext()) {
            list.add(
                Entry(
                    id = c.getLong(0), path = c.getString(1), hash = c.getString(2),
                    size = c.getLong(3), mime = c.getString(4), detectedAt = c.getLong(5),
                    status = c.getInt(6), retries = c.getInt(7), tag = c.getString(8),
                ),
            )
        }
        c.close()
        return list
    }

    private class DbHelper(ctx: Context) : SQLiteOpenHelper(ctx, DB_NAME, null, DB_VERSION) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE $TABLE(" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "path TEXT," +
                    "hash TEXT UNIQUE," +
                    "size INTEGER DEFAULT 0," +
                    "mime TEXT," +
                    "detected_at INTEGER DEFAULT 0," +
                    "status INTEGER DEFAULT 0," +
                    "retries INTEGER DEFAULT 0," +
                    "tag TEXT" +
                    ")",
            )
            db.execSQL("CREATE INDEX idx_hash ON $TABLE(hash)")
            db.execSQL("CREATE INDEX idx_status ON $TABLE(status)")
            db.execSQL("CREATE INDEX idx_date ON $TABLE(detected_at)")
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            db.execSQL("DROP TABLE IF EXISTS $TABLE")
            onCreate(db)
        }
    }
}
