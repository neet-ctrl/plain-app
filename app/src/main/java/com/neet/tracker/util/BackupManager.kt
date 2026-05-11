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

    private val SHARED_PREF_NAMES = listOf("uv_viewer", "neet_chapter_library")

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
        "reminders"
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
     * Merges data — existing records are kept; backup records are INSERT OR REPLACE'd.
     * Returns total row count merged on success.
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

            var rowsRestored = 0

            // 2. Merge Room database
            val backupDbFile = File(restoreDir, "database.db")
            if (backupDbFile.exists()) {
                val srcDb = SQLiteDatabase.openDatabase(
                    backupDbFile.absolutePath,
                    null,
                    SQLiteDatabase.OPEN_READONLY
                )
                val dstDb = db.openHelper.writableDatabase
                dstDb.beginTransaction()
                try {
                    ALL_TABLES.forEach { table ->
                        runCatching {
                            srcDb.rawQuery("SELECT * FROM $table", null).use { cursor ->
                                if (cursor.moveToFirst()) {
                                    val colNames     = cursor.columnNames
                                    val cols         = colNames.joinToString(",")
                                    val placeholders = colNames.joinToString(",") { "?" }
                                    do {
                                        val vals = Array<Any?>(colNames.size) { i ->
                                            when (cursor.getType(i)) {
                                                Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(i)
                                                Cursor.FIELD_TYPE_FLOAT   -> cursor.getDouble(i)
                                                Cursor.FIELD_TYPE_BLOB    -> cursor.getBlob(i)
                                                Cursor.FIELD_TYPE_NULL    -> null
                                                else                      -> cursor.getString(i)
                                            }
                                        }
                                        runCatching {
                                            dstDb.execSQL(
                                                "INSERT OR REPLACE INTO $table ($cols) VALUES ($placeholders)",
                                                vals
                                            )
                                            rowsRestored++
                                        }
                                    } while (cursor.moveToNext())
                                }
                            }
                        }
                    }
                    dstDb.setTransactionSuccessful()
                } finally {
                    dstDb.endTransaction()
                    srcDb.close()
                }
            }

            // 3. Copy files — only add files that don't yet exist (merge, not overwrite)
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

            // 4. Restore SharedPreferences (backup values are merged over current)
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

            restoreDir.deleteRecursively()
            rowsRestored
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun addToZip(zos: ZipOutputStream, file: File, entryName: String) {
        zos.putNextEntry(ZipEntry(entryName))
        file.inputStream().use { it.copyTo(zos) }
        zos.closeEntry()
    }
}
