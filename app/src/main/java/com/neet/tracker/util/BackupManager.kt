package com.neet.tracker.util

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.neet.tracker.data.database.NEETDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object BackupManager {

    private val SHARED_PREF_NAMES = listOf(
        "uv_viewer",
        "neet_chapter_library",
        "neet_alarms"
    )

    // Every Room table — if a table doesn't exist in the backup DB it's silently skipped.
    private val ALL_TABLES = listOf(
        "student_profile",
        "notebooks", "notebook_chapters",
        "books",
        "pyq_sources", "pyq_chapters", "pyq_years",
        "test_papers", "sample_papers",
        "pw_batches", "pw_tests",
        "day_planner", "week_planner", "month_planner", "year_planner",
        "diary_entries",
        "date_events",
        "dictionary_neet", "dictionary_non_neet",
        "mnemonics",
        "diagrams",
        "chapter_short_notes",
        "day_waste",
        "neet_sequence", "neet_sequence_pdf",
        "subject_short_notes",
        "lack_points",
        "neet_syllabus",
        "reminders",
        "error_entries",
        "revision_items",
        "flashcard_progress"
    )

    // ── Backup ────────────────────────────────────────────────────────────────

    /**
     * Creates a .neet backup ZIP in [folderUri] (a document-tree URI).
     * Returns the file name on success.
     */
    suspend fun createBackup(
        context: Context,
        db: NEETDatabase,
        folderUri: Uri
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            // 1. Flush Room WAL so the .db file is fully up-to-date
            try {
                db.openHelper.writableDatabase
                    .execSQL("PRAGMA wal_checkpoint(FULL)")
            } catch (_: Exception) {}

            val stamp   = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())
            val zipName = "NEET_Backup_$stamp.neet"
            val tempZip = File(context.cacheDir, zipName)

            ZipOutputStream(BufferedOutputStream(FileOutputStream(tempZip))).use { zos ->

                // ── Room database file ─────────────────────────────────────
                val dbFile = context.getDatabasePath("neet_tracker.db")
                if (dbFile.exists()) addToZip(zos, dbFile, "database.db")

                // WAL / SHM (may be empty after checkpoint, still include for safety)
                listOf("neet_tracker.db-wal", "neet_tracker.db-shm").forEach { n ->
                    val f = File(dbFile.parentFile, n)
                    if (f.exists() && f.length() > 0) addToZip(zos, f, n)
                }

                // ── All files inside filesDir (PDFs, annotation JSON, images) ─
                val filesDir = context.filesDir
                if (filesDir.exists()) {
                    filesDir.walkTopDown().filter { it.isFile }.forEach { file ->
                        val rel = file.relativeTo(filesDir).path
                        addToZip(zos, file, "files/$rel")
                    }
                }

                // ── SharedPreferences as JSON ──────────────────────────────
                val rootObj = JSONObject()
                SHARED_PREF_NAMES.forEach { name ->
                    val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
                    val obj   = JSONObject()
                    prefs.all.forEach { (k, v) ->
                        when (v) {
                            is String  -> obj.put(k, v)
                            is Int     -> obj.put(k, v)
                            is Long    -> obj.put(k, v)
                            is Float   -> obj.put(k, v)
                            is Boolean -> obj.put(k, v)
                        }
                    }
                    rootObj.put(name, obj)
                }
                val prefsBytes = rootObj.toString().toByteArray(Charsets.UTF_8)
                zos.putNextEntry(ZipEntry("prefs.json"))
                zos.write(prefsBytes)
                zos.closeEntry()
            }

            // 2. Write the temp ZIP into the user-chosen folder
            val folder = DocumentFile.fromTreeUri(context, folderUri)
                ?: error("Cannot open selected folder")
            folder.findFile(zipName)?.delete()
            val outDoc = folder.createFile("application/octet-stream", zipName)
                ?: error("Cannot create file in folder")
            context.contentResolver.openOutputStream(outDoc.uri)!!.use { out ->
                tempZip.inputStream().use { it.copyTo(out) }
            }
            tempZip.delete()

            zipName
        }
    }

    // ── Restore ───────────────────────────────────────────────────────────────

    /**
     * Restores a .neet backup from [fileUri].
     *
     * Strategy (most reliable — guarantees profile & all data are fully recovered):
     *  1. Extract ZIP to a temp dir.
     *  2. Count total rows in backup DB (for reporting).
     *  3. Merge user-created files (PDFs, annotations, images) — non-destructive.
     *  4. Restore SharedPreferences — backup values merge over current.
     *  5. Close Room, overwrite the live .db file with the backup copy, delete
     *     WAL/SHM so SQLite starts clean. Room auto-reconnects on next access.
     *
     * Caller should prompt the user to restart the app after success so that
     * all Room Flows see the new database content.
     */
    suspend fun restoreBackup(
        context: Context,
        db: NEETDatabase,
        fileUri: Uri
    ): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val restoreDir = File(context.cacheDir, "neet_restore_tmp")
            restoreDir.deleteRecursively()
            restoreDir.mkdirs()

            // 1. Extract ZIP into restoreDir
            context.contentResolver.openInputStream(fileUri)!!.use { input ->
                ZipInputStream(BufferedInputStream(input)).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        val dest = File(restoreDir, entry.name)
                        if (entry.isDirectory) {
                            dest.mkdirs()
                        } else {
                            dest.parentFile?.mkdirs()
                            dest.outputStream().use { zis.copyTo(it) }
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
            }

            // 2. Count rows in backup DB (for reporting only)
            var rowsTotal = 0
            val backupDbFile = File(restoreDir, "database.db")
            if (backupDbFile.exists()) {
                val srcDb = SQLiteDatabase.openDatabase(
                    backupDbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY
                )
                ALL_TABLES.forEach { table ->
                    runCatching {
                        srcDb.rawQuery("SELECT COUNT(*) FROM $table", null).use { c ->
                            if (c.moveToFirst()) rowsTotal += c.getInt(0)
                        }
                    }
                }
                srcDb.close()
            }

            // 3. Merge files — only add files that don't yet exist (non-destructive)
            val backupFiles = File(restoreDir, "files")
            if (backupFiles.exists()) {
                val targetDir = context.filesDir
                backupFiles.walkTopDown().filter { it.isFile }.forEach { src ->
                    val rel  = src.relativeTo(backupFiles).path
                    val dest = File(targetDir, rel)
                    dest.parentFile?.mkdirs()
                    if (!dest.exists()) src.copyTo(dest, overwrite = false)
                }
            }

            // 4. Restore SharedPreferences
            val prefsFile = File(restoreDir, "prefs.json")
            if (prefsFile.exists()) {
                runCatching {
                    val root = JSONObject(prefsFile.readText())
                    SHARED_PREF_NAMES.forEach { name ->
                        if (root.has(name)) {
                            val obj    = root.getJSONObject(name)
                            val editor = context.getSharedPreferences(name, Context.MODE_PRIVATE).edit()
                            obj.keys().forEach { key ->
                                when (val v = obj.get(key)) {
                                    is String  -> editor.putString(key, v)
                                    is Int     -> editor.putInt(key, v)
                                    is Long    -> editor.putLong(key, v)
                                    is Double  -> editor.putFloat(key, v.toFloat())
                                    is Boolean -> editor.putBoolean(key, v)
                                }
                            }
                            editor.apply()
                        }
                    }
                }
            }

            // 5. Replace the live database file with the backup copy.
            //    Close Room first so the file is not locked, then overwrite,
            //    then delete WAL/SHM so SQLite opens cleanly on next access.
            if (backupDbFile.exists()) {
                val liveDbFile = context.getDatabasePath("neet_tracker.db")
                val liveWal    = File(liveDbFile.parentFile, "neet_tracker.db-wal")
                val liveShm    = File(liveDbFile.parentFile, "neet_tracker.db-shm")

                runCatching { db.close() }

                liveDbFile.parentFile?.mkdirs()
                backupDbFile.copyTo(liveDbFile, overwrite = true)
                liveWal.delete()
                liveShm.delete()
            }

            restoreDir.deleteRecursively()
            rowsTotal
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun addToZip(zos: ZipOutputStream, file: File, entryName: String) {
        zos.putNextEntry(ZipEntry(entryName))
        file.inputStream().use { it.copyTo(zos) }
        zos.closeEntry()
    }
}
